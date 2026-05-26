import axiosInstance from './axiosInstance'

/**
 * YOLOv8 servisine (port 5001) yapılan çağrılar.
 * Geliştirme ortamında /yolo prefix'i vite proxy ile yönetilir.
 * Production'da nginx /yolo → yolov8-service:5001 proxy'si gereklidir.
 */

/** Son batch detection sonuçlarını döndürür. */
export async function getYoloStatus() {
  const res = await axiosInstance.get('/yolo/status')
  return res.data
}

/** YOLOv8 servisinin sağlık durumunu döndürür. */
export async function getYoloHealth() {
  const res = await axiosInstance.get('/yolo/health')
  return res.data
}

/**
 * Belirli bir zone için manuel detection tetikler.
 * @param {number} zoneId
 * @param {string|null} imageBase64 - null ise sentetik sayım kullanılır
 */
export async function triggerDetect(zoneId, imageBase64 = null) {
  const res = await axiosInstance.post('/yolo/detect', {
    zone_id: zoneId,
    image_base64: imageBase64,
  })
  return res.data
}

/** Tüm zone'lar için batch detection tetikler. */
export async function triggerBatchDetect() {
  const res = await axiosInstance.post('/yolo/detect/batch')
  return res.data
}
