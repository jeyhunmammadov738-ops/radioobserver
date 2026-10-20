# Phase 3 (3.1) — UX Contract, Navigation and Copy Inventory

Status: IMPLEMENTED (2026-10-20). Default user-facing language: **Turkish**.
Developer-approved decisions: (1) 3.1+3.2 together, 3.3–3.10 unstarted; (2) Turkish UI;
(3) after permissions are granted observation MUST NOT start automatically — a NEW
explicit Start tap is required; (4) public observations stay masked, with no reveal control.

This is an implementation-ready specification. It contains NO Compose code: actual
screens belong to tasks 3.5–3.9. Copy keys referenced here correspond to the typed
`UiCopyKey` / `SessionIssueKey` enums in `:core:radio` (`...core.radio.ui`).

---

## 1. Screens and surfaces

| # | Ekran | Amaç | Ana durumlar |
|---|---|---|---|
| 1 | **Ana Ekran / Gözlem Kontrolü** | Özelliği açıklar; ayrı **Bluetooth Başlat** ve **Wi-Fi Gözle** eylemleri, her radyo için ayrı Durdur, kalan süre ve radyo durumu | Etkin değil, kontrol ediliyor, izin gerekli, gözlemliyor, durduruluyor, bitti, süre doldu, kullanılamaz, hata |
| 2 | **Gözlemler** | Oturuma ait **geçici** birleşik akış; BLE/Wi-Fi filtresi | Boş (kanıt değil), gözlemliyor, bayat/bilinmeyen veri, süresi dolan/çıkarılan kayıtlar |
| 3 | **Gözlem Detayı** | Güven düzeyi, üretici/kanıt ipuçları, RSSI kategorisi, tazelik, kaynak | Belirsiz kimlik, sinyal sınırları, yaş kaynağı açıklamaları |
| 4 | **İzinler ve Hazırlık** | Bağlamsal gerekçe; platform izin istekleri; donanım/konum anahtarı kurtarma | Verilmemiş, yalnızca yaklaşık, reddedildi, gerekçe gerekli, ayar yönlendirmesi, kapalı servisler, desteklenmeyen donanım |
| 5 | **Gizlilik ve Hakkında** | Yükleme/kalıcı veri/arka plan takibi olmadığını ve taramanın ne kanıtlamadığını açıklar | Düz dil; sahte güvenlik güvencesi yok |

Tasarım yönü: profesyonel Material 3, ölçülü durum bileşenleri, etiketli radyo simgeleri,
okunaklı tipografi, erişilebilirlik etiketleri ve geniş dokunma hedefleri; telefonlarda
tek sütun, geniş ekranlarda basit iki sütun uyarlaması. Görsel cila "gözlemliyor",
"gözlemlendi" ve "doğrulandı" ayrımını asla gizlemez.

## 2. Navigation flow

```
Ana Ekran ──Başlat/İzin──▶ [İzinler ve Hazırlık] ──izin sonucu──▶ Ana Ekran (YENİ Başlat beklenir)
    │  └──Gözlemlere git──▶ Gözlemler ──satır seçimi──▶ Gözlem Detayı ──geri──▶ Gözlemler
    ├──Gizlilik ve Hakkında (her zaman erişilebilir)
    └──İzinler ve Hazırlık (her zaman erişilebilir; Ana ekrandaki durum kartlarından)
```

Kurallar:
- Hiçbir navigasyon, ekran oluşumu (composition), uygulamaya dönüş (resume) veya yeniden
  oluşturma (rotation/process recreation) gözlem **başlatmaz**.
- Gözlemler ekranı yalnızca aktif oturumun geçici kayıtlarını gösterir; geçmiş yoktur.
- Detay ekranı yalnızca eldeki satır kimliğiyle (oturum-yerel) açılır; kalıcı bağlantı yok.

## 3. Screen-state transitions (per radio, independent)

```
IDLE ─Başlat dokunuşu─▶ CHECKING ─┬─(hazır)──────────────▶ OBSERVING ─süre doldu─▶ TIMED_OUT
                                  │                            │ └─Durdur─▶ STOPPING ─▶ STOPPED
                                  ├─(izin eksik)─▶ PERMISSION_REQUIRED ─(izin sonucu)─▶ IDLE  [YENİ Başlat gerekir]
                                  └─(donanım/servis)─▶ BLOCKED ──(sorun giderildi + YENİ Başlat)─▶ CHECKING
OBSERVING/BLOCKED/PERMISSION_REQUIRED ──(uygulama ön plandan çıktı)──▶ STOPPED (FOREGROUND_LOST)
Herhangi bir durum ──(beklenmeyen hata)──▶ ERROR (otomatik yeniden deneme YOK)
```

- BLE ve Wi-Fi durumları **birbirinden bağımsız**tır; birini durdurmak diğerini etkilemez
  (tek istisna: ön plan kaybı ikisini birlikte sonlandırır — Phase 2 politikası).
- `APPROXIMATE_ONLY` konum izni FINE yerine geçmez; BLE/Wi-Fi gözlemi
  `PERMISSION_REQUIRED` + `PERMISSION_APPROXIMATE_ONLY` durumunda kalır.

## 4. İzin davranışı (kesin sözleşme)

1. **Birinci Başlat dokunuşu:** hazırlık kontrolü yapılabilir; gerekli izinler (sonraki
   yetkili görevde) istenebilir. Bu dokunuş gözlemi başlatmaz.
2. **İzin diyaloğu sonrası:** gözlem **otomatik başlamaz**. İzin sonucu yalnızca hazırlık
   durumunu günceller.
3. **İkinci Başlat dokunuşu:** yeni ve açık bir kullanıcı eylemi gerekir; ardından taze
   hazırlık ve ön plan (RESUMED) kontrolü yapılır.
4. Aynı "otomatik başlatma yok" kuralı şunlar için de geçerlidir: ekran oluşumu, uygulamaya
   dönüş, navigasyon dönüşü, ekran döndürme, süreç yeniden başlatma, ayarlardan dönüş.
5. Android 12+ için **ACCESS_FINE_LOCATION ve ACCESS_COARSE_LOCATION birlikte** istenir;
   yalnızca yaklaşık sonuç FINE gerektiren işlemleri karşılamaz.
6. `shouldShowRequestPermissionRationale() == false` tek başına "kalıcı reddedildi" kanıtı
   değildir; gerçek istek geçmişi + muhafazakâr yönlendirme kullanılır (3.3).
7. Reddedilen izin hiçbir durumda sessizce gözlem başlatmaz veya tekrar tekrar sormaz.

## 5. Turkish copy inventory

| Key | Türkçe metin (taslak) | Kullanıldığı yer |
|---|---|---|
| `HOME_INTRO` | "Bu ekran açıkken yakındaki Bluetooth sinyallerini ve sistemde bulunan Wi-Fi sonuçlarını gözlemleyebilirsiniz." | Ana ekran giriş |
| `BLE_START_BUTTON` | "Bluetooth gözlemini başlat" | Ana ekran, BLE kartı |
| `BLE_STOP_BUTTON` | "Bluetooth gözlemini durdur" | Ana ekran, BLE kartı |
| `WIFI_START_BUTTON` | "Kullanılabilir Wi-Fi sonuçlarını gözle" | Ana ekran, Wi-Fi kartı |
| `WIFI_STOP_BUTTON` | "Wi-Fi gözlemini durdur" | Ana ekran, Wi-Fi kartı |
| `WIFI_PASSIVE_SOURCE_NOTE` | "Wi-Fi sonuçları sistemden gelir; eski veya eksik olabilir. Bu uygulama Wi-Fi taraması başlatmaz." | Wi-Fi kartı açıklaması |
| `PRECISE_LOCATION_RATIONALE` | "Android, bu tarama sonuçları için hassas konum izni gerektirir. Coğrafi konumunuzu göstermiyor ve sonuçları yüklemiyoruz." | İzin gerekçesi |
| `APPROXIMATE_ONLY_HINT` | "Yaklaşık konum açık, ancak bu gözlem özelliği hassas erişim gerektirir. Seçiminizi ayarlardan değiştirebilirsiniz." | Yaklaşık-tek durumu |
| `EMPTY_FEED_HONEST_NOTE` | "Bu oturumda gösterilecek gözlem yok. Bu, yakında cihaz olmadığını kanıtlamaz." | Boş akış durumu |
| `CONFIDENCE_CAUTION` | "Sinyal düzenine göre olası cihaz ailesi; kimlik doğrulanmadı." | Detay ekranı |
| `SIGNAL_QUALITATIVE_NOTE` | "Sinyal kategorisi kaba bir radyo ipucudur, kesin mesafe değildir." | Detay ekranı |
| `BACKGROUND_STOP_NOTE` | "Gözlem, aktif ekrandan ayrıldığınızda durur. Kendiliğinden yeniden başlamaz." | Ana ekran / Gizlilik |
| `PRIVACY_ABOUT_BODY` | "Hiçbir gözlem yüklenmez, diske yazılmaz veya paylaşılmaz. Veriler yalnızca bu oturum boyunca cihazda, bellekte tutulur ve oturum bittiğinde silinir." | Gizlilik ve Hakkında |

Durum/kilit metinleri (Ana ekran kartları, tip anahtarlarıyla):
- `IDLE` → "Etkin değil"; `CHECKING` → "Kontrol ediliyor…"; `OBSERVING` → "Gözlemliyor (kalan: {saniye} sn)";
  `STOPPING` → "Durduruluyor…"; `STOPPED` → "Durdu"; `TIMED_OUT` → "Süre doldu";
  `BLOCKED` → "Kullanılamıyor"; `ERROR` → "Hata"; `PERMISSION_REQUIRED` → "İzin gerekli".
- `SessionIssueKey` → Türkçe: `BLUETOOTH_OFF` "Bluetooth kapalı"; `WIFI_OFF` "Wi-Fi kapalı";
  `LOCATION_SERVICES_OFF` "Konum servisleri kapalı"; `PERMISSION_DENIED` "İzin verilmedi";
  `PERMISSION_APPROXIMATE_ONLY` "Yalnızca yaklaşık konum verildi (hassas konum gerekli)";
  `SECURITY_EXCEPTION` "Güvenlik nedeniyle tarama engellendi"; `SCAN_THROTTLED` "Sistem taramayı geçici olarak sınırladı";
  `HARDWARE_UNSUPPORTED` "Bu cihaz gerekli donanımı desteklemiyor"; `FOREGROUND_LOST` "Ekrandan ayrıldığınız için durduruldu";
  `INTERNAL_ERROR` "Beklenmeyen bir hata oluştu".

Erişilebilirlik etiketleri (content description): her Başlat/Durdur düğmesi eylem + radyo
adını birlikte söyler ("Bluetooth gözlemini başlat, düğme"); durum kartları durumu ve
kalan süreyi okur; tazelik rozeti "taze / eski / bilinmiyor" olarak seslendirilir.

## 6. Honesty rules (metin düzeyinde zorunlu)

Yasak ifadeler ve nedenleri:
- Doğrulanmış AirTag/SmartTag kimliği iddiası (üretici ipucu kanıt değildir) → yalnızca "olası cihaz ailesi".
- RSSI'den metre cinsinden mesafe → yalnızca nitel kategori ("yakın/uzak" hissi değil, "sinyal kategorisi").
- Güvenlik/tehdit garantisi ("takipçi bulundu", "güvendesiniz") → yasak.
- Bayat veya bilinmeyen Wi-Fi sonucunu "yeni/taze" göstermek → yasak; tazelik rozeti dürüst olmalı.
- Ham adres/BSSID gösterme veya gösterme seçeneği → yasak (dev kararı: maskeli kalır).
- Boş listeyi "yakında cihaz yok" kanıtı saymak → yasak; `EMPTY_FEED_HONEST_NOTE` kullanılır.
- Wi-Fi önbellek sonucunu canlı tarama gibi sunmak → yasak (`WIFI_PASSIVE_SOURCE_NOTE`).

## 7. Layout, tema ve erişilebilirlik gereksinimleri

- Material 3, açık/koyu tema; metin-kontrast en az WCAG AA (4.5:1 gövde, 3:1 büyük metin).
- Dokunma hedefleri ≥ 48dp; durum için yalnızca renge bağlı anlatım yasak (metin + simge).
- Telefon: tek sütun; ≥600dp genişlik: iki sütun (kontroller | akış), aynı durum modeli.
- Yazı tipi ölçekleme (font scale) 200%'e kadar okunabilirlik; uzun Türkçe metinler sarmalı.
- Tüm durumlar için ekran okuyucu duyuruları (canlı bölge) planlanır; 3.9'da uygulanır.
- Metinler string kaynaklarına taşınacak (3.5); bu belge kaynak metnin tek referansıdır.

## 8. Kenar durumlar ve senaryolar

| Senaryo | Beklenen davranış |
|---|---|
| Ekran oluşumu / uygulamaya dönüş | Hiçbir şey başlamaz; durum IDLE (veya önceki STOPPED/FOREGROUND_LOST kalır) |
| Ekran döndürme / süreç yeniden başlatma | Aktif oturum geri yüklenmez; veri geri getirilmez; IDLE |
| İzin diyaloğu açıkken duraklama | Dialog sonucu geldiğinde gözlem başlamaz; yalnızca hazırlık güncellenir |
| Ayarlardan dönüş | Hazırlık yeniden değerlendirilir; gözlem ancak YENİ Başlat ile |
| Gözlem sırasında ön plan kaybı | Her iki oturum STOPPED (FOREGROUND_LOST); dönüşte otomatik başlamaz |
| Süre dolması | TIMED_OUT; kalan süre 0'da durur, geri sayım "süre doldu" olur |
| Boş Wi-Fi önbelleği | Boş durum + dürüst not; "taze sonuç yok" ifadesi |
| Konum yalnızca yaklaşık | PERMISSION_REQUIRED + yaklaşık-tek metni; tarama yok |
| Donanım/servis kapalı | BLOCKED + tip anahtarı; ayar yönlendirmesi sunulur (otomatik açma yok) |
| Beklenmeyen hata | ERROR + tip anahtarı; otomatik yeniden deneme yok |

## 9. Acceptance criteria (3.1 DoD)

- [x] Beş ekran/yüzey amaç ve durumlarıyla tanımlı (Bölüm 1).
- [x] Navigasyon akışı ve durum geçişleri (Bölüm 2–3), otomatik başlatma yasağı dahil.
- [x] Ayrı BLE ve Wi-Fi Başlat/Durdur kontrolleri planlandı.
- [x] Tam Türkçe kopya envanteri (Bölüm 5), tip anahtarlarıyla eşleşiyor.
- [x] İzin davranışı: birinci dokunuş / diyalog sonrası / ikinci dokunuş sözleşmesi (Bölüm 4).
- [x] Dürüstlük kuralları ve yasak iddialar (Bölüm 6).
- [x] Düzen/tema/kontrast/erişilebilirlik gereksinimleri (Bölüm 7).
- [x] Boş, bayat, bilinmeyen, zaman aşımı, kullanılamaz, hata, duraklama, yeniden oluşturma senaryoları (Bölüm 8).
- [x] Ham adres/BSSID gösterme kontrolü yok (dev kararı).

Gerçek cihaz davranışı (izin diyaloğu görünümü, TalkBack, OEM düzeni, canlı alım)
**UNVERIFIED** — bu belge yalnızca sözleşme düzeyinde doğrulanabilir.
