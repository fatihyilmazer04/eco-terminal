-- V12__add_fcm_token.sql
-- user_profiles tablosuna FCM token alanı eklenir
-- Firebase Cloud Messaging push bildirimleri için cihaz tokenı saklanır

ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS fcm_token VARCHAR(500);
