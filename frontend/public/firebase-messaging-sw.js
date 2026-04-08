// Firebase Cloud Messaging Service Worker
// =========================================
// Bu dosya PWA push bildirimlerini arka planda almak için kullanılır.
//
// Kurulum için:
//   1. Firebase Console > Project Settings > General > Your apps > Firebase SDK snippet
//   2. Aşağıdaki firebaseConfig değerlerini gerçek proje değerleriyle doldurun
//   3. firebase.json veya hosting config ile /firebase-messaging-sw.js'i serve edin
//
// Dokümantasyon: https://firebase.google.com/docs/cloud-messaging/js/receive

importScripts('https://www.gstatic.com/firebasejs/9.0.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/9.0.0/firebase-messaging-compat.js');

// TODO: Firebase Console'dan alınan gerçek config ile değiştirin
const firebaseConfig = {
  apiKey:            "YOUR_API_KEY",
  authDomain:        "YOUR_PROJECT_ID.firebaseapp.com",
  projectId:         "YOUR_PROJECT_ID",
  storageBucket:     "YOUR_PROJECT_ID.appspot.com",
  messagingSenderId: "YOUR_SENDER_ID",
  appId:             "YOUR_APP_ID",
};

firebase.initializeApp(firebaseConfig);
const messaging = firebase.messaging();

messaging.onBackgroundMessage((payload) => {
  console.log('[firebase-messaging-sw.js] Background message:', payload);
  const { title, body } = payload.notification ?? {};
  self.registration.showNotification(title ?? 'Eco-Terminal', {
    body: body ?? '',
    icon: '/eco-terminal-icon.png',
    badge: '/eco-terminal-badge.png',
  });
});
