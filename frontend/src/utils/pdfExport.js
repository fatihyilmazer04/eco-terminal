/**
 * Eco-Terminal — PDF Rapor Dışa Aktarma
 *
 * Yaklaşım:
 *  - Header HTML divini html2canvas ile yakalar (Türkçe karakterler tarayıcı
 *    tarafından render edildiği için bozulmaz).
 *  - Sekme içeriğini html2canvas ile yakalar (dark-mode ekran görüntüsü).
 *  - jsPDF ile A4 PDF oluşturur; içerik çok uzunsa otomatik sayfa ekler.
 *  - Footer metni ASCII-safe tutulur (jsPDF gömülü Helvetica'sı Türkçe
 *    desteklemez; header/body tamamen canvas görüntüsü olduğu için sorun yok).
 */

export async function exportTabPdf({
  contentRef,   // React ref — yakalanacak sekme içerik diви
  tabLabel,     // "Yoğunluk" | "Enerji" | "Kullanıcı" | "AI Özet"
  rangeLabel,   // "Son 30 Gün" vb.
  dateRange,    // "28 Nis 2026 – 28 May 2026"
  filename,     // "eco-terminal-yogunluk-raporu-2026-05-28.pdf"
  onStart,
  onEnd,
}) {
  onStart?.()

  try {
    // Dinamik import — paketler sadece PDF üretilirken yüklenir
    const [{ jsPDF }, { default: html2canvas }] = await Promise.all([
      import('jspdf'),
      import('html2canvas'),
    ])

    const element = contentRef?.current
    if (!element) return

    const captureW = element.scrollWidth || element.offsetWidth || 900

    // ── 1. Header HTML oluştur (Türkçe metin tarayıcıda render edilir) ──
    const now = new Date()
    const generatedAt = now.toLocaleDateString('tr-TR', {
      day: 'numeric', month: 'long', year: 'numeric',
    }) + ' ' + now.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' })

    const headerEl = document.createElement('div')
    headerEl.setAttribute('aria-hidden', 'true')
    headerEl.style.cssText = [
      `position:fixed`,
      `left:${-(captureW + 200)}px`,
      `top:0`,
      `width:${captureW}px`,
      `font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif`,
      `line-height:1`,
      `z-index:-1`,
    ].join(';')

    headerEl.innerHTML = `
      <div style="
        background:#2ecc71;color:#ffffff;
        padding:20px 30px;
        display:flex;justify-content:space-between;align-items:center;
        box-sizing:border-box;width:100%;
      ">
        <div>
          <div style="font-size:22px;font-weight:700;letter-spacing:-0.5px;">Eco-Terminal</div>
          <div style="font-size:13px;margin-top:6px;opacity:0.9;">${tabLabel} Raporu</div>
        </div>
        <div style="text-align:right;font-size:11px;line-height:2;opacity:0.9;">
          <div>Dönem: <strong>${rangeLabel}</strong></div>
          <div>${dateRange}</div>
          <div>Oluşturuldu: ${generatedAt}</div>
        </div>
      </div>
      <div style="height:4px;background:linear-gradient(90deg,#27ae60,#2ecc71,#a8edca);"></div>
    `
    document.body.appendChild(headerEl)

    const headerCanvas = await html2canvas(headerEl, {
      scale: 2,
      backgroundColor: '#2ecc71',
      logging: false,
      useCORS: true,
    })
    document.body.removeChild(headerEl)

    // ── 2. Sekme içeriğini yakala ──
    const contentCanvas = await html2canvas(element, {
      scale: 1.5,
      backgroundColor: '#111827',   // Tailwind gray-900 (dark tema arka plan)
      logging: false,
      useCORS: true,
      allowTaint: true,
      scrollX: 0,
      scrollY: 0,
      x: 0,
      y: 0,
      width: element.scrollWidth,
      height: element.scrollHeight,
      windowWidth: element.scrollWidth || window.innerWidth,
    })

    // ── 3. PDF oluştur ──
    const pdf = new jsPDF({ unit: 'mm', format: 'a4', orientation: 'portrait' })
    const PW = 210, PH = 297, M = 10, CW = PW - 2 * M   // A4, 10 mm kenar boşluğu
    const FOOTER_H = 14   // footer alanı (mm)

    const headerH_MM    = (headerCanvas.height  / headerCanvas.width)  * CW
    const contentTot_MM = (contentCanvas.height / contentCanvas.width) * CW

    // Sayfa kapasiteleri
    const firstPageAvail = PH - M - headerH_MM - 6 - FOOTER_H
    const otherPageAvail = PH - 2 * M - FOOTER_H

    // İçeriği sayfalara böl
    const pages = []
    let remaining  = contentTot_MM
    let srcStart   = 0
    let isFirst    = true

    while (remaining > 0.5) {
      const avail  = isFirst ? firstPageAvail : otherPageAvail
      const sliceH = Math.min(remaining, avail)
      pages.push({
        srcStart,
        sliceH,
        destY: isFirst ? M + headerH_MM + 6 : M,
      })
      remaining -= sliceH
      srcStart  += sliceH
      isFirst    = false
    }

    // Sayfaları render et
    for (let i = 0; i < pages.length; i++) {
      if (i > 0) pdf.addPage()
      const p = pages[i]

      const srcY_px   = (p.srcStart / contentTot_MM) * contentCanvas.height
      const sliceH_px = Math.max(1, Math.round((p.sliceH / contentTot_MM) * contentCanvas.height))

      const sc = document.createElement('canvas')
      sc.width  = contentCanvas.width
      sc.height = sliceH_px
      sc.getContext('2d').drawImage(
        contentCanvas,
        0, Math.round(srcY_px), contentCanvas.width, sliceH_px,
        0, 0,                   contentCanvas.width, sliceH_px,
      )

      pdf.addImage(
        sc.toDataURL('image/jpeg', 0.87),
        'JPEG', M, p.destY, CW, p.sliceH,
      )
    }

    // 1. sayfaya header ekle (içeriğin üstüne)
    pdf.setPage(1)
    pdf.addImage(headerCanvas.toDataURL('image/png'), 'PNG', M, M, CW, headerH_MM)

    // Tüm sayfalara footer ekle
    const totalPages = pages.length
    for (let i = 1; i <= totalPages; i++) {
      pdf.setPage(i)
      pdf.setDrawColor(200, 200, 200)
      pdf.line(M, PH - FOOTER_H + 2, PW - M, PH - FOOTER_H + 2)
      pdf.setFontSize(7)
      pdf.setTextColor(160, 160, 160)
      // ASCII-safe — jsPDF Helvetica Türkçe karakter barındırmaz
      pdf.text('Eco-Terminal - Akilli Havalimani Yonetim Sistemi', M, PH - 5)
      pdf.text(`Sayfa ${i} / ${totalPages}`, PW - M, PH - 5, { align: 'right' })
    }

    pdf.save(filename)
  } finally {
    onEnd?.()
  }
}
