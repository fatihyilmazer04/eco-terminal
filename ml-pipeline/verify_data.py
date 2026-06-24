#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_data.py
==============
Üretilen sentetik verinin kalitesini ayrıntılı biçimde denetler.

- Kayıt sayısı ve NaN kontrolü
- Zone eşitliği
- Saatlik histogram (ASCII grafik)
- Mevsimsel karşılaştırma
- Yıllık büyüme (COVID düşüşü dahil)
- Enerji istatistikleri
- Zone tipi dağılımı
- Opsiyonel matplotlib grafikleri (kurulu değilse atlanır)

Kullanım:
    python verify_data.py
"""

import sys
import numpy as np
import pandas as pd
from pathlib import Path

DATA_FILE = Path(__file__).parent / 'data' / 'synthetic_terminal_data.parquet'

# Türkçe ay isimleri
AY_ISIMLERI = [
    '', 'Oca', 'Şub', 'Mar', 'Nis', 'May', 'Haz',
    'Tem', 'Ağu', 'Eyl', 'Eki', 'Kas', 'Ara'
]

GUN_ISIMLERI = ['Pzt', 'Sal', 'Çar', 'Per', 'Cum', 'Cmt', 'Paz']


def ascii_bar(value: float, max_val: float = 1.0, width: int = 35) -> str:
    """0–max_val arasındaki değeri ASCII blok çubuğa dönüştürür."""
    filled = int(value / max_val * width)
    return '█' * filled + '░' * (width - filled)


def section(title: str) -> None:
    print(f'\n{"─" * 70}')
    print(f'  {title}')
    print('─' * 70)


def main() -> None:
    # ── Dosya kontrolü ────────────────────────────────────────────────────────
    if not DATA_FILE.exists():
        print('✗ Veri dosyası bulunamadı.')
        print(f'  Beklenen konum: {DATA_FILE}')
        print('  Önce generate_synthetic_data.py çalıştırın:')
        print('    python generate_synthetic_data.py')
        sys.exit(1)

    print('=' * 70)
    print('  ECO-TERMİNAL  ─  Sentetik Veri Kalite Raporu')
    print('=' * 70)
    print(f'\nDosya yükleniyor: {DATA_FILE}', end='  ', flush=True)

    df = pd.read_parquet(DATA_FILE)
    df['timestamp'] = pd.to_datetime(df['timestamp'])
    df['year']  = df['timestamp'].dt.year
    df['month'] = df['timestamp'].dt.month
    df['hour']  = df['timestamp'].dt.hour
    df['dow']   = df['timestamp'].dt.dayofweek

    print(f'({len(df):,} satır yüklendi)')

    # ── [1] Genel istatistikler ───────────────────────────────────────────────
    section('1. GENEL İSTATİSTİKLER')
    n = len(df)
    print(f'  Toplam kayıt       : {n:,}')
    print(f'  Zone sayısı        : {df["zone_id"].nunique()}')
    ts_min = df['timestamp'].min()
    ts_max = df['timestamp'].max()
    print(f'  Zaman aralığı      : {ts_min.date()} → {ts_max.date()}')
    print(f'  Gün sayısı         : {(ts_max - ts_min).days + 1:,}')
    nan_total = df.isnull().sum().sum()
    print(f'  NaN değer          : {nan_total}  {"✓ temiz" if nan_total == 0 else "✗ HATA"}')
    print(f'  density_pct aralık : [{df["density_pct"].min():.4f} – {df["density_pct"].max():.4f}]')
    print(f'  people_count maks  : {df["people_count"].max():,}')
    print(f'  energy_kwh ort.    : {df["energy_kwh"].mean():.4f} kWh/15dk')

    # ── [2] Zone başına kayıt sayısı ─────────────────────────────────────────
    section('2. ZONE BAŞINA KAYIT SAYISI')
    zone_counts = df.groupby(['zone_id', 'zone_name'])['timestamp'].count().reset_index()
    zone_counts.columns = ['zone_id', 'zone_name', 'count']
    zone_counts = zone_counts.sort_values('zone_id')
    max_c = zone_counts['count'].max()
    for _, row in zone_counts.iterrows():
        bar = ascii_bar(row['count'], max_c, 25)
        print(f"  {row['zone_name']:<20} {row['count']:>8,}  {bar}")
    delta = zone_counts['count'].max() - zone_counts['count'].min()
    print(f'\n  Maks fark: {delta:,} kayıt  '
          f'{"✓" if delta < 1000 else "~ (2026 yıl kesiminden beklenen)"}')

    # ── [3] Saatlik yoğunluk profili ─────────────────────────────────────────
    section('3. SAATLİK ORTALAMA DOLULUK  (tüm zone\'lar, tüm yıllar)')
    hourly = df.groupby('hour')['density_pct'].mean()
    max_h  = hourly.max()
    print('  Saat  Ort.Doluluk  Grafik')
    print('  ─────────────────────────────────────────────────')
    for h, v in hourly.items():
        bar    = ascii_bar(v, max_h, 30)
        marker = ' ← pik' if v >= hourly.quantile(0.85) else ''
        print(f'  {h:02d}:00   {v:.4f}      {bar}{marker}')
    peak_h = sorted(hourly.nlargest(4).index.tolist())
    low_h  = sorted(hourly.nsmallest(3).index.tolist())
    print(f'\n  En yoğun saatler : {peak_h}  (06–09 + 14–22 beklenir)')
    print(f'  En sakin saatler : {low_h}  (01–04 beklenir)')

    # ── [4] Haftalık dağılım ─────────────────────────────────────────────────
    section('4. HAFTALıK ORT. DOLULUK')
    weekly = df.groupby('dow')['density_pct'].mean()
    max_w  = weekly.max()
    for d, v in weekly.items():
        bar = ascii_bar(v, max_w, 30)
        print(f'  {GUN_ISIMLERI[d]}   {v:.4f}  {bar}')

    # ── [5] Mevsimsel karşılaştırma ──────────────────────────────────────────
    section('5. MEVSİMSEL KARŞILAŞTIRMA')
    season_map = {
        'İlkbahar (Mar-May)': [3, 4, 5],
        'Yaz      (Haz-Ağu)': [6, 7, 8],
        'Sonbahar (Eyl-Kas)': [9, 10, 11],
        'Kış      (Ara-Şub)': [12, 1, 2],
    }
    season_avgs = {}
    for name, months in season_map.items():
        avg = df[df['month'].isin(months)]['density_pct'].mean()
        season_avgs[name] = avg

    max_s = max(season_avgs.values())
    for name, avg in season_avgs.items():
        bar = ascii_bar(avg, max_s, 30)
        print(f'  {name}  {avg:.4f}  {bar}')

    summer = season_avgs['Yaz      (Haz-Ağu)']
    winter = season_avgs['Kış      (Ara-Şub)']
    ok = '✓ yaz > kış — beklenen örüntü' if summer > winter else '✗ HATA: yaz < kış'
    print(f'\n  {ok}')

    # ── [6] Yıllık büyüme (COVID dahil) ──────────────────────────────────────
    section('6. YILLIK ORT. DOLULUK  (COVID düşüşü + toparlanma görülmeli)')
    yearly = df.groupby('year')['density_pct'].mean().sort_index()
    max_y  = yearly.max()
    for yr, v in yearly.items():
        bar   = ascii_bar(v, max_y, 30)
        covid = '  ← COVID' if yr in [2020, 2021] else ''
        print(f'  {yr}  {v:.4f}  {bar}{covid}')

    y16 = yearly.get(2016, np.nan)
    y25 = yearly.get(2025, np.nan)
    if not np.isnan(y16) and not np.isnan(y25):
        growth = (y25 - y16) / y16 * 100
        ok = '✓' if y25 > y16 else '✗ HATA'
        print(f'\n  2016→2025 büyüme: %{growth:+.1f}  {ok}')

    # ── [7] Enerji istatistikleri ─────────────────────────────────────────────
    section('7. ENERJİ İSTATİSTİKLERİ  (kWh / 15dk)')
    energy_stats = df.groupby('zone_type')['energy_kwh'].agg(
        Ortalama='mean', Min='min', Maks='max', StdSapma='std'
    ).round(4)
    print(energy_stats.to_string())

    # ── [8] Zone tipine göre ort. doluluk ────────────────────────────────────
    section('8. ZONE TİPİNE GÖRE ORT. DOLULUK')
    type_avg = df.groupby('zone_type')['density_pct'].mean().sort_values(ascending=False)
    max_t    = type_avg.max()
    for ztype, v in type_avg.items():
        bar = ascii_bar(v, max_t, 30)
        print(f'  {ztype:<12}  {v:.4f}  {bar}')

    # ── [9] Dosya bilgisi ────────────────────────────────────────────────────
    section('9. DOSYA BİLGİSİ')
    size_mb = DATA_FILE.stat().st_size / 1024 / 1024
    print(f'  Boyut  : {size_mb:.1f} MB  (Snappy sıkıştırma)')
    print(f'  Konum  : {DATA_FILE}')
    print(f'  Sütunlar: {list(df.columns)}')

    print('\n' + '=' * 70)
    print('  ✓ Doğrulama tamamlandı.')
    print('=' * 70)

    # ── [10] Opsiyonel matplotlib grafikleri ─────────────────────────────────
    try:
        import matplotlib.pyplot as plt
        import matplotlib.gridspec as gridspec

        print('\nMatplotlib bulundu — grafikler oluşturuluyor...')

        fig = plt.figure(figsize=(16, 12))
        gs  = gridspec.GridSpec(2, 3, figure=fig, hspace=0.40, wspace=0.35)
        fig.suptitle('Eco-Terminal Sentetik Veri — Kalite Grafikleri', fontsize=15, fontweight='bold')

        # (a) Saatlik doluluk
        ax1 = fig.add_subplot(gs[0, 0])
        hourly.plot(ax=ax1, kind='bar', color='steelblue', alpha=0.85)
        ax1.set_title('Saatlik Ort. Doluluk')
        ax1.set_xlabel('Saat'); ax1.set_ylabel('Ort. Doluluk')
        ax1.tick_params(axis='x', rotation=0, labelsize=7)

        # (b) Yıllık trend
        ax2 = fig.add_subplot(gs[0, 1])
        colors = ['#e74c3c' if y in [2020, 2021] else '#2ecc71' for y in yearly.index]
        yearly.plot(ax=ax2, kind='bar', color=colors, alpha=0.85)
        ax2.set_title('Yıllık Ort. Doluluk  (kırmızı=COVID)')
        ax2.set_xlabel('Yıl'); ax2.set_ylabel('Ort. Doluluk')
        ax2.tick_params(axis='x', rotation=45, labelsize=8)

        # (c) Aylık mevsimsel
        ax3 = fig.add_subplot(gs[0, 2])
        monthly_avg = df.groupby('month')['density_pct'].mean()
        monthly_avg.index = [AY_ISIMLERI[m] for m in monthly_avg.index]
        monthly_avg.plot(ax=ax3, kind='bar', color='coral', alpha=0.85)
        ax3.set_title('Aylık Ort. Doluluk  (Mevsimsel)')
        ax3.set_xlabel('Ay'); ax3.set_ylabel('Ort. Doluluk')
        ax3.tick_params(axis='x', rotation=45, labelsize=8)

        # (d) Haftalık dağılım
        ax4 = fig.add_subplot(gs[1, 0])
        weekly.index = [GUN_ISIMLERI[d] for d in weekly.index]
        weekly.plot(ax=ax4, kind='bar', color='mediumpurple', alpha=0.85)
        ax4.set_title('Haftalık Ort. Doluluk')
        ax4.set_xlabel('Gün'); ax4.set_ylabel('Ort. Doluluk')
        ax4.tick_params(axis='x', rotation=0)

        # (e) Zone tipine göre doluluk
        ax5 = fig.add_subplot(gs[1, 1])
        type_avg.plot(ax=ax5, kind='barh', color='teal', alpha=0.85)
        ax5.set_title('Zone Tipine Göre Ort. Doluluk')
        ax5.set_xlabel('Ort. Doluluk')

        # (f) Enerji vs. Doluluk dağılımı (örneklem)
        ax6 = fig.add_subplot(gs[1, 2])
        sample = df.sample(n=min(5000, len(df)), random_state=42)
        for ztype, grp in sample.groupby('zone_type'):
            ax6.scatter(grp['density_pct'], grp['energy_kwh'],
                        label=ztype, alpha=0.3, s=4)
        ax6.set_title('Doluluk – Enerji İlişkisi')
        ax6.set_xlabel('Doluluk Oranı')
        ax6.set_ylabel('Enerji (kWh/15dk)')
        ax6.legend(fontsize=8)

        plot_path = DATA_FILE.parent / 'validation_plots.png'
        plt.savefig(str(plot_path), dpi=130, bbox_inches='tight')
        print(f'  Grafik kaydedildi: {plot_path}')
        plt.show()

    except ImportError:
        print('\n(matplotlib kurulu değil → pip install matplotlib  ile grafik ekleyebilirsiniz)')


if __name__ == '__main__':
    main()
