# Radio Observer Advanced — Professional UI/UX Redesign Roadmap

**Sürüm:** UIUX-RM v1.0  
**Hazırlanma:** 2026-11-06  
**Durum:** Kullanıcı tarafından tasarım yol haritasının oluşturulması onaylandı; uygulama aşamaları henüz başlatılmadı.  
**Hedef platform:** Mevcut Android / Kotlin / Jetpack Compose Material 3 uygulaması  
**Kapsam:** İşlevsel olarak kabul edilmiş uygulamanın kullanıcı deneyimi ve görsel tasarımının yeniden ele alınması; veri veya radyo semantiğinin değiştirilmemesi.

> **Yönetim kararı:** Orijinal `roadmap.md` içindeki Phase 1–3, 23/23 görev ve tüm tarihsel kanıtlar korunur. Fieldwatch genişleme işleri WS01–WS08 tamamlanmış kapsam olarak değerlendirilir (WS07 fizibilite araştırmasıdır, konum/Remote ID uygulaması değildir). Bu yeni plan **Phase 4 değildir**. Bağımsız `UX-00`–`UX-09` çalışma hattıdır. Fiziksel Xiaomi kabulü geliştirici tarafından bildirildi; geçmiş OpenCode host/JVM test sonuçları ayrı kanıt düzeyindedir.

## A. Hedef ve ürün ilkeleri

**Önerilen ürün yönü:** *Control Deck* — güvenilir, dingin, bilgi yoğunluğunu ayarlayabilen, modern teknik gözlem aracı. Koyu arayüz önceliklidir; gösterişli radar, yanıltıcı cihaz konumu, 'gizli dinleme' estetiği, dekoratif animasyon ve aşırı cam/neon efektleri kullanılmaz.

İlkeler:
1. **Durum > eylem > veri > açıklama:** O anda tarama olup olmadığı, birincil kontrol ve sonuçların ne ifade ettiği ilk bakışta anlaşılır.
2. **Tek ana eylem:** Her bağlamda belirgin tek ana eylem; ikincil eylemler uygun hiyerarşide.
3. **Açık durumlar:** Pasif Wi-Fi'nin eski olabilecek sonuçları ve oturum sonu temizleme davranışı yanlış temsil edilmez.
4. **Tek bileşen dili:** Renk, ikon, tipografi, kart, yüzey, liste, chip, toggle ve eylem menüleri ortak tasarım token'larından gelir.
5. **Progressive disclosure:** Uzun açıklamalar ve nadiren kullanılan seçenekler gerekliyse ayrıntı/destek ekranlarına taşınır.
6. **Gerçek veriye dürüstlük:** Kayıt sayısı cihaz sayısı değildir; RSSI kesin mesafe/yön vermez; şematik Radar harita değildir.
7. **Veri kaybetmeden yenileme:** Mevcut SQLite/dosya depolarına, izin akışlarına, tarama süresine, onay ve saklama sözleşmelerine dokunulmaz.
8. **Ölçülebilir kabul:** Her paket gerçek ekran karşılaştırması + kod/host test kanıtı + gerekliyse Xiaomi kabulüyle kapanır.

## B. Görsel kanıt — yedi Xiaomi ekranı

| Görsel | Ekran / durum | Tespit | Öncelik | Beklenen düzeltme |
|---|---|---|---|---|
| 01 (`image.png`) | Gözlem; uyarılar kapalı | Çok büyük BLE/Wi-Fi kartları, tekrar eden açıklamalar, ana durumun zayıf vurgusu, açık gri sistem çubuğu ile koyu arayüz kopukluğu | P0 | Kısa oturum durum paneli, kompakt kaynak kontrol kartları, net başlat/durdur eylemleri, edge-to-edge/inset düzeni |
| 02 (`image(1).png`) | Uyarılar açık | Yedi uzun tetikleyicinin düzensiz yığılması; seçilmiş/kapalı durumun zayıf ifadesi | P0 | Ayrı uyarı ayar alanı veya açılır alt panel; çoklu seçim chip/switch grupları; özet ve durum |
| 03 (`image(2).png`) | Gizlilik metni açık | Çok uzun yasal/teknik metin kontrol ekranını büyütüyor | P1 | Düzenli bilgi/gizlilik ekranı, kısa özet, detaylara isteğe bağlı erişim |
| 04 (`image(3).png`) | Gözlemler; veri yok | Boş durum mesajı iki kere, görünüm/filtre kontrolleri düzensiz, eylemsiz büyük boş alan | P0 | Tek bir anlamlı boş durum, gözlem sekmesine yönlendirme, iki katmanlı net görünüm/filtre hiyerarşisi |
| 05 (`image(4).png`) | Geçmiş; 26 kayıt | Dışa aktar/grup/favori eylemleri eşit ve dağınık, çok yüksek kayıt kartları, 'Sil' diğer eylemlere yakın | P0 | Geçmiş başlığı/araç çubuğu, tutarlı filtre satırı, daha kompakt kayıt satırları, onaylı ikincil silme menüsü |
| 06 (`image(5).png`) | Ayarlar üst | Çok geniş ayar satırları, bağlamı belirsiz 'Ayrıntı' bağlantıları, saklama/silme yetersiz ayrım | P1 | Bölümlü ayar listeleri, izin ve kayıt açıklamaları, ayrı tehlikeli işlem alanı |
| 07 (`image(6).png`) | Ayarlar alt | Parçalı kartlar, katalog yönetimi ile sürüm bilgisinin karışması, fazla dikey boşluk | P1 | Gizlilik / Veri yönetimi / Katalog / Hakkında ayrımı, kompakt bilgilendirici satırlar |

**Görsellerin sınırı:** Radar dolu hali, Zaman tüneli, analiz grubu detayları, karşılaştırmalar, dışa aktarma ekranı, Filtreler, katalog ve FieldDiag-04 gösterilmedi. Bunlar UX-00 sırasında kaynak kod, ekran envanteri ve mevcut ekran yakalamaları ile ayrıca denetlenecek; görülmemiş bir ekran için mevcut sorunu varmış gibi raporlamayın.

## C. Tasarım sistemi hedef sözleşmesi

Bunlar **başlangıç tasarım kriterleri**, henüz onaylanmış nihai renk değerleri değildir.

- **Temel kimlik:** Sakin koyu zemin, kontrollü indigo vurgu, sınırlı turkuaz kaynak ayrımı, nötr yüzeyler ve açık okunabilir metin. Renk tek başına bilgi taşımaz.
- **Renk rolleri:** `background`, `surface`, `surfaceContainer*`, `primary/onPrimary`, `secondary`, `outline`, `error/onError`, `success` (özel uygulama token'ı), `disabled`. Android Material3 rollerini kullan; rastgele hex değerlerini ekranlara dağıtma.
- **Tipografi:** Başlık, bölüm başlığı, satır etiketi, yardımcı metin, veri/istatistik numarası için sistemli Material3 ölçeği; gereksiz büyük sayfa başlıklarından kaçın; Türkçe uzun metin ve tarih formatı için düzenli satır kırılması.
- **Aralık/yoğunluk:** 4dp tabanlı ritim; 8/12/16/24dp öncelikli boşluklar. Boşluk azaltımı dokunma hedeflerini küçültmemeli.
- **Dokunma:** Etkileşimli hedefler en az **48×48dp**; küçük yıldız, menü, silme ikonlarının hit-area'sı da dahil.
- **Kontrast:** Normal metinde **en az 4.5:1**, büyük metinde **en az 3:1** WCAG AA hedefi; seçili/pasif/uyarı durumlarında yalnız renk kullanılmaz.
- **Şekiller:** Sınırlı, açık tanımlı 3–4 radius seviyesi; bütün butonlar aynı tipografik ve yükseklik kurallarını izler.
- **Kontroller:** Tek seçim için segmented button; çoklu filtre için FilterChip; açık/kapalı için Switch; yıkıcı işlem için ayrı onay diyalogu; uzun seçenekler için bottom sheet veya uygun ekran.
- **İkonlar:** Tutarlı tek ikon ailesi ve boyut hiyerarşisi; dekoratif ikon yerine anlamlı durum ikonları; ikon-only eylemlerde açıklayıcı erişilebilir ad.
- **Durumlar:** Idle / scanning / finished / permission missing / unavailable / cached / error / empty / loading / populated / saving / cancelled / saved gibi gerçek durumlar açık tasarlanır.
- **Sistem çubukları:** Android 15+ edge-to-edge, status/navigation bar kontrastı, Safe Insets, IME açılışı ve çift inset uygulanmasını önleme.
- **Uyarlanabilirlik:** Önce mevcut Xiaomi dikey telefon görünümü; sonra compact/medium/expanded ve yatay/dar yükseklik; ekran yeniden boyutlanırken önemli gezinme ve geçici UI durumu korunur.
- **Hareket:** Yalnız anlamlı, kısa ve erişilebilir durum geçişleri; gerçek tarama ilerlemesi yoksa yapay progress animasyonu yok.
- **Kullanıcının önceki test kararı:** Açık tema, 2× yazı boyutu ve TalkBack için özel manuel test planı oluşturulmayacak. Buna rağmen kod seviyesinde temel okunabilirlik, hit-area ve semantik etiket kalitesi korunacak; yapılmamış testler yapıldı diye raporlanmayacak.

## D. İş paketleri ve bağımlılıklar

**Kapsam:** `UX-00`–`UX-09`, toplam **10** tasarım paketi. Her paket ayrı, küçük ve gerçek işlevselliği koruyan kod değişikliği olarak yürütülecek; tek seferlik büyük yeniden yazım yapılmayacak.

### UX-00 — Baseline, ekran envanteri ve tasarım sözleşmesi
**Öncelik:** P0 · **Tür:** Denetim / planlama · **Build:** OFF, kaynak kodu değiştirilmez.

- [ ] Mevcut `roadmap.md` ilk okunur, tarihsel 23 görev ve WS01–08 kanıtları korunur.
- [ ] Ekranlar, alt ekranlar, dialogs, bottom sheets, menüler, durumlar ve yönlendirmeler koddan eksiksiz envanterlenir.
- [ ] Görülmeyen ekranlar için gerçek veya deterministik önizleme bazları toplanır; ekran verileri sanitizasyona tabidir.
- [ ] User journey envanteri: gözlemi başlat/bitir, gözlem incele/filtrele, Geçmiş, favoriler/notlar, gruplar, karşılaştırma, CSV/JSON, kayıt izinleri.
- [ ] Eski ekrana yeni hedef eşlemesi ve bileşen envanteri hazırlanır; veri/izin durumlarının bozulmayacağı sınırlar yazılır.
- [ ] Temel ekran hiyerarşisi ve tasarım token adayları belirlenir; sonraki fazlar için yeterli hafif wireframe/akış tanımlanır.

**Teslimatlar:** `docs/uiux/screen-inventory.md`, `docs/uiux/design-contract.md`, ekran-durum matrisi, baseline görseller.  
**DoD:** Tüm ekranlar kapsanmış; bilinmeyenler açık işaretli; tek hedef tasarım yönü seçilmiş, yeni kod/izin yok.

### UX-01 — Foundation: Theme, tipografi ve yeniden kullanılabilir bileşenler
**Öncelik:** P0 · **Bağımlılık:** UX-00 · **Build:** ON, Debug Only.

- [ ] Material3 renk rolleri, typography, shape ve spacing token'ları tek modülde merkezileştirilir.
- [ ] Radio source badge, stat row, section header, state banner, action row, compact observation row, confirm dialog, empty/error state oluşturulur.
- [ ] Primary / secondary / text / destructive action hiyerarşisi kod seviyesinde birleştirilir.
- [ ] Seçili, pasif, izin bekleyen, hata ve yükleniyor durumları ortak gösterilir.
- [ ] İkon ve hit-area ölçümleri; kontrast test raporu yapılır. Yeni bağımlılık eklenmesi varsayılan yasaktır.

**DoD:** Tek token sistemi; kodda birbirinden kopuk ekran-bazlı stiller azalır; ana bileşenler parametrik, önizlenebilir ve testlidir; davranış değişmez.

### UX-02 — Global shell: 4 sekme, başlıklar, system bars, gezinme
**Öncelik:** P0 · **Bağımlılık:** UX-01 · **Build:** ON.

- [ ] `Gözlem / Gözlemler / Geçmiş / Ayarlar` dört ana sekmesi aynı isim ve semantiklerle korunur.
- [ ] Tek Scaffold/safe-inset sahipliği, sistem üst/alt bar uyumu, kaydırma ve IME politikası düzenlenir.
- [ ] Tutarlı app bar, ekran başlığı, geri dönüş, üst eylem/overflow modelleri uygulanır.
- [ ] Sekme geçişinde kullanıcıya ait kalıcı veri ve mevcut izin/tarama yaşam döngüsü korunur; arayüz geçişi tarama başlatmaz.
- [ ] Dar/yatay görünümde kırpılmayan eylem konumları belirlenir.

**DoD:** Koyu app ile sistem barları tutarlı; 4 sekme çalışır; içerik barların altında gizlenmez; veri ve tarama davranışı aynı kalır.

### UX-03 — Gözlem: Operasyon paneli ve oturum kontrolleri
**Öncelik:** P0 · **Bağımlılık:** UX-02 · **Build:** ON.

- [ ] En üstte kısa, belirgin **oturum durumu**: etkin değil / BLE aktif / Wi-Fi aktif / ikisi aktif (gerçek durum modeline bağlı).
- [ ] BLE ve Wi-Fi, aynı tasarım sistemli kompakt kontrol bloklarına dönüştürülür. Kaynaklar bağımsız kalır.
- [ ] Birincil Başlat/Durdur eylemleri net ve ilgili izin engeli bağlamında görünür olur; izin sonrası ayrı başlatma dokunuşu korunur.
- [ ] Uzun açıklamalar tek satırlık yardımcı metin + ayrıntı bağlantısına dönüşür; yanlış tazelik/konum iddiaları üretilmez.
- [ ] Uyarılar için kısa kontrol/özet; gelişmiş tetikleyicilerin ayrıntıları UX-08 ile düzenlenir.
- [ ] Gizlilik özeti ana ekranda kısa tutulur; uzun açıklama bağlantılı bilgi alanına gider.

**DoD:** İlk ekranda oturum durumu ve ana kontroller zahmetsiz görünür; BLE/Wi-Fi ayrımı, 20/30 sn sınırlar ve foreground davranışı değişmez.

### UX-04 — Gözlemler: Liste / şematik Radar / Zaman tüneli / filtreler
**Öncelik:** P0 · **Bağımlılık:** UX-03 · **Build:** ON.

- [ ] Görünüm seçimi tek tutarlı segmented control; kaynak ve favori filtreleri ayrı, tutarlı chip/filtre katmanı olur.
- [ ] Yinelenmiş boş mesaj kaldırılır; bir adet durum açıklaması ve **Gözlem ekranına git** eylemi kullanılır (otomatik tarama yok).
- [ ] Canlı satırların bilgi hiyerarşisi yeniden tasarlanır: kaynak, tazelik, sinyal kategorisi, üretici/sınıflandırma ipucu; bilinmeyen değerler dürüstçe gösterilir.
- [ ] Radar yalnız şematik kalır; yakınlık, yön, gerçek cihaz sayısı ve GPS iddiası yok. Yerleştirme/sayaç invaryantı korunur.
- [ ] Zaman tünelinde boşluklar, eski veri, sıfır kayıt, seçili kayıt ve oturum sonu durumları tasarlanır.
- [ ] Filtre/önayar yönetimi ayrı katmanda açık seçili durumlarla yeniden düzenlenir; mevcut saklanan filtreler korunur.

**DoD:** 3 görünüm, filtre ve favori işlevsel eşdeğer; boş durum tekil; kaynak ve tazelik doğru; canlı oturum verileri yeni kalıcı kimlik üretmez.

### UX-05 — Geçmiş: Kayıt listesi, detay, favori, etiket ve veri kontrolü
**Öncelik:** P0 · **Bağımlılık:** UX-02/UX-01 · **Build:** ON.

- [ ] Başlıkta kayıt sayısı ve net filtre bağlamı; rapor/analiz/favori eylemleri tutarlı üst menü veya destekleyici eylem alanında gruplanır.
- [ ] Büyük kartlar yerine okunur kompakt kayıt satırları; başlık, kaynak, kayıt tarihi/tazelik, favori ve ayrıntı net hiyerarşi.
- [ ] `Sil` eylemi kazara dokunmayı zorlaştıran bağlamsal menü + onay üzerinden sunulur. Tek silme semantiği korunur.
- [ ] Kayıt ayrıntısında mevcut etiket/not/favori alanlarının form deneyimi düzenlenir; açıklama kaydının yalnız kayıt UUID'sine bağlı olduğu korunur.
- [ ] Silinmiş ve süresi geçmiş kayıtların grup/analiz/ihracat ekranlarından geri getirilmemesi regresyonla doğrulanır.

**DoD:** Kayıt kaybı yok; silme kazara tetiklenmez; 7/14/30 gün ve 500 sınır korunur; seçilen kayıt/detay ve gezinme tutarlı.

### UX-06 — Analiz grupları, veri grafikleri ve karşılaştırmalar
**Öncelik:** P1 · **Bağımlılık:** UX-05 · **Build:** ON.

- [ ] Analiz grup listesi, grup ayrıntısı, açık üyelik seçimi, oluştur/adlandır/sil akışları ortak düzenle yenilenir.
- [ ] BLE/Wi-Fi payları, sinyal/tazelik, 6 zaman kovası ve karşılaştırma okunabilir bilgi mimarisine dönüşür.
- [ ] Grafikler görsel olarak anlaşılır; metinsel gerçek sayılarla tutarlı kalır. Ara ölçüm, mesafe veya cihaz kimliği uydurulmaz.
- [ ] İki grup karşılaştırması kompakt telefonda okunur, orta/geniş alanda uygun sütun düzeneğine dönüşebilir.
- [ ] Eksik/silinmiş kayıtlar için dürüst boş/eksik sayım durumları korunur.

**DoD:** Ekran sayıları saf WS05 analytics motoruyla birebir örtüşür; yanlış fiziksel cihaz sayısı iddiası yok; grup ve kayıt saklama davranışı değişmez.

### UX-07 — Raporlama ve CSV/JSON dışa aktarma deneyimi
**Öncelik:** P1 · **Bağımlılık:** UX-05/UX-06 · **Build:** ON.

- [ ] Export kapsamı, CSV/JSON formatı, kayıt sayısı, gizli alan tercihi, kaydet eylemi açık adımlarla sunulur.
- [ ] SSID, kullanıcı etiketi/notu varsayılan dışlanmış görünür; dahil etme kullanıcının ayrı, bilinçli seçimine bağlıdır.
- [ ] Sistem dosya seçiciye geçiş, kaydetme başarı/hata/iptal durumları dürüst ve görünür olur.
- [ ] Geçmiş/analiz sayılarının export çıktısıyla tutarlılığı korunur; rapor açılması tarama veya kayıt başlatmaz.
- [ ] Paylaşım veya import/restore gibi yeni fonksiyon eklenmez.

**DoD:** WS06 CSV/JSON bayt içeriği, şema ve gizlilik kuralları bozulmaz; kullanıcı kaydetme/iptal durumunu anlar; gerçek SAF kontrolü Xiaomi'de yapılır.

### UX-08 — Ayarlar, tetikleyiciler, sınıflandırma kataloğu ve gizlilik
**Öncelik:** P1 · **Bağımlılık:** UX-01/UX-03 · **Build:** ON.

- [ ] Ayarlar Veri ve Geçmiş / Gizlilik / Sınıflandırma / Hakkında başlıkları altında düzenlenir; açık 'Ayrıntı' bağlantıları bağlama özel etiketlenir.
- [ ] Geçmiş kaydı ve SSID onayları ayrı kalır. Retention seçenekleri tutarlı single-choice control olur.
- [ ] “Tüm geçmişi sil” tehlikeli eylemi görsel ve yapısal olarak diğer ayarlardan ayrılır; kuvvetli açık onay korunur.
- [ ] Uyarı tetikleyicileri yedi dev düğme yerine okunur, çoklu seçilebilir gruplar ve seçim özetiyle gösterilir; uyarı varsayılanı OFF kalır.
- [ ] Gizlilik açıklamaları tutarlı başlıklar ve kısa paragraf katmanlarıyla verilir; belirsiz iddia veya gereksiz tekrar bırakılmaz.
- [ ] Sınıflandırma kataloğu oluşturma/değiştirme/silme ve 30 öğe sınırı korunarak aynı bileşen sistemiyle yenilenir.
- [ ] FieldDiag-04 debug-only ve yedi dokunuşla açılma semantiği korunur; ana navigasyona eklenmez.

**DoD:** Ayarlar ve uyarıların kullanıcı onayları değişmez; uzun açıklamalar anlaşılır; silme ve katalog eylemleri güvenilir; gizli geliştirici ekranına erişim sözleşmesi aynı.

### UX-09 — Bütünleşik görsel kalite, regresyon ve Xiaomi son kabulü
**Öncelik:** P0 son kapı · **Bağımlılık:** UX-00–UX-08 · **Build:** ON, Debug Only.

- [ ] Tüm Compose ekranları ve gerçek durumları, önce/sonra yakalamalarıyla karşılaştırılır; gizli/ağ adları örneklerde maskelenir.
- [ ] Yatay/compact/medium/expanded planları, minimum 48dp dokunma, metin kontrastı, taşma/çakışma, status/nav bar ve IME sınırları denetlenir.
- [ ] Gradle final-tree Core JVM + App JVM + compileDebugKotlin + assembleDebug + lintDebug doğrulanır; yeni regresyon testleri yapılır.
- [ ] Xiaomi üzerinde üzerine kurulum, uygulama açılışı, gezinme, tarama, izin akışı, ön plan kaybı, kayıt koruması ve tüm ana kullanıcı yolculukları doğrulanır.
- [ ] CSV/JSON gerçek SAF kayıtları ve gizlilik varsayılanları yeniden denenir; kalıcı veri kaybı olmaması kontrol edilir.
- [ ] Kalan görünüm hataları önceliklendirilir; P0/P1 tasarım kusurları giderilir; ertelemeler açık yazılır.
- [ ] UIUX roadmap tamamlandı kararı yalnız son gerçek cihaz kabulüyle kaydedilir.

**DoD:** Görsel hiyerarşi, tutarlılık ve kullanılabilirlik kabul edildi; yeni kritik UI sorunu yok; mevcut 419 host/JVM testi tarihsel taban olarak korunup final sonuçlar taze yeniden hesaplandı; gerçek Xiaomi kabulü tamamlandı.

## E. Önerilen yürütme sırası, teslim kanıtı ve bağımlılık haritası

| Paket | Çıktı | Bağımlılık | Efor göreli | Kabul kanıtı |
|---|---|---|---|---|
| UX-00 | Tam ekran envanteri / tasarım sözleşmesi | Yok | M | İncelenmemiş ekran listesi, wireframe akışları |
| UX-01 | Merkezî tema / ortak bileşenler | UX-00 | L | Token ve bileşen önizlemeleri + host kapıları |
| UX-02 | Tutarlı app shell / 4 sekme / insets | UX-01 | M | Sistem çubukları ve dört ana sekme kabulü |
| UX-03 | Gözlem operasyon paneli | UX-02 | M | Idle/aktif/engelli/bitmiş durum ekranları |
| UX-04 | Gözlemler / üç görünüm / filtre | UX-03 | L | Canlı/boş/Eski/filtre çapraz kontrol |
| UX-05 | Geçmiş / kayıt detayları | UX-01/02 | L | Veri koruma, satır yoğunluğu, silme/geri dönüş |
| UX-06 | Analitik / grafikler / karşılaştırma | UX-05 | M | İstatistik tutarlılığı ve okunabilirlik |
| UX-07 | Raporlama ve ihracat | UX-05/06 | M | CSV/JSON, SAF, gizlilik akışları |
| UX-08 | Ayarlar / uyarılar / katalog | UX-01/03 | L | Kaydedilmiş tercihler ve izin sınırları |
| UX-09 | Son bütünleşik QA | Hepsi | L | Taze testler + son Xiaomi kabulü |

Efor sınıfları sadece göreli karmaşıklıktır; takvim veya teslim tarihi sözü değildir.

## F. Sıfır fonksiyonel regresyon sözleşmesi

### Değiştirilmeyecek davranışlar
- Paket kimliği `com.hjinlabs.radioobserver`; mevcut imza, sürüm ve üzerine kurulum uyumluluğu.
- 4 alt sekme, mevcut navigation destinations ve geri/ileri gezinme semantiği.
- Kullanıcı tarafından ayrıca başlatılan BLE ve pasif Wi-Fi gözlemleri; süre sınırları; `RESUMED`/arka plana geçişte durma.
- `WifiManager.startScan` ve `WifiScanner.startScan` kullanılmaması; taramanın izin verilmesi üzerine kendiliğinden başlamaması.
- Kaydedilen Geçmiş, 7/14/30 gün saklama ve 500 kayıt sınırı; ayrı SSID kayıt onayı; tek silme / tümünü silme sözleşmeleri.
- Üç canlı görünüm, filtre önayarları, yerleşik sınıflandırma kuralları, kullanıcı kataloğu, kayıt-temelli favoriler/notlar/etiketler, oturumluk uyarılar.
- Eski Wi-Fi verisinin **Eski** olarak kalması; hiçbir ekranın bilgiyi tazeleştirilmiş gibi sunmaması.
- WS05 gruplarında yalnız açık üyelik; cihaz-kimlerliği/oturumlararası eşleme yapılmaması.
- WS06 CSV/JSON içerik, şema, hassas alanların varsayılan gizlenmesi, Android SAF ve kaydetme iptali.
- WS07 fizibilite notları: GPS/harita/Remote ID, mesafe/yön/ısı haritası veya takip eklenmez.
- MainActivity'de Context bağımlılıklarının `super.onCreate` öncesi yaratılmaması.
- `FieldDiag-04` debug-only, yedi dokunuş, ham verinin dışa aktarılmaması.

### Her kod paketinin asgari doğrulaması
1. Kod değişmeden önce proje roadmap, Git staged/unstaged/untracked ve gerçek Gradle yapılandırması incelenir.
2. Değişiklik yüzeyi yalnız tasarım hedefiyle ilgili ekran/bileşen dosyaları ve dar davranış regresyon testleri olur.
3. Anlamlı etkileşim değişiklikleri için mevcut host/JVM altyapısıyla dar kapsamlı davranış testi yazılır.
4. Final kod ağacında gerekli Gradle görevleri `--no-daemon` ve önceden belirlenmiş zaman sınırıyla çalıştırılır.
5. Önceki WS08 tabanı: **370 Core JVM + 49 App JVM; lint 0 hata / 15 uyarı**, yalnız tarihsel referanstır. Yeni sonuçlar yeniden hesaplanır.
6. Yeni Debug APK'nın tam yolu, dosya boyutu, SHA-256 ve değiştirilen ekran envanteri raporlanır.
7. OpenCode cihaz/emülatör/ADB çalıştırmaz; gerçek Xiaomi UI doğrulamasını kullanıcı yapar.
8. **Build Mode:** kod paketleri ON/Debug Only, UX-00 OFF. **Git Commit/Push Mode:** her yeni talimatta açık izin yoksa OFF. `roadmap.md` tarihsel içerik korunur.
9. Başarılı iş paketi için bir Türkçe teknik rapor + görsel önce/sonra kanıtı + Xiaomi kontrol listesi hazırlanır.

## G. Uçtan uca kabul senaryoları

| Yolculuk | Başarılı deneyim ölçütü |
|---|---|
| İlk açılış, tarama yok | Durum açık; tekil yönlendirme; yanlış 'hiç cihaz yok' iddiası bulunmaz |
| BLE/Wi-Fi başlangıç ve durdurma | Ana kontroller hızlı bulunur; izin sonrası ayrı başlatma dokunuşu kalır |
| Eski Wi-Fi sonuçlarını görme | Eski/önbellek semantiği net, taze cihaz bulgusu gibi sunulmaz |
| Gözlemler boşken Liste/Radar/Zaman tüneli | Filtre/görünüm kontrolleri taşmaz; tek boş durum; gerçek tarama otomatik başlamaz |
| Filtreleme ve favoriler | Seçim durumu açık; reset/önayar mantığı bozulmaz |
| Geçmiş kayıtlarını inceleme ve silme | Kompakt okunur liste; detay açık; yıkıcı işlem onaylı; diğer veri korunur |
| Oturum gruplama ve analiz | İstatistikler kayıtlara bağlıdır; grafikler sayılarla çelişmez |
| Dışa aktarma | Kapsam ve gizlilik tercihi görünür; CSV/JSON doğru; iptal güvenli |
| Ayarlar ve gizlilik | Saklama/onay/silme ayrışmış; saklanan seçimler kaybolmaz |
| Uyarılar | Varsayılan OFF; seçili tetikleyiciler açık; oturum bitince temizlenir |
| Sistem barları / ekran boyutu değişimi | İçerik kırpılmaz; bottom navigation/klavye ile çakışmaz |
| Uygulamanın yeniden açılması | Kalıcı History/metadata korunur, transient live state veya tarama otomatik başlamaz |

## H. Başarı ölçütleri ve bitiş kararı

1. **0** yinelenmiş boş durum mesajı; her ekranda tek anlamlı boş/engel/açık durum açıklaması.
2. **0** görünmeyen veya yatay kesilen temel etkileşim (kapsanan Xiaomi ve dokümante pencere boyutlarında).
3. **100%** kritik etkileşim için en az 48dp dokunma alanı, açıklanabilir seçili durum ve doğru semantik.
4. Metin kontrastı normal 4.5:1 / büyük 3:1 hedefini karşılar; kişisel veri test görsellerinde bulunmaz.
5. Kazara veri silmeye yönelik koruma ve onay ekranı korunur.
6. Dört ana ekran ve bütün ilgili alt ekranlar ortak tasarım sistemini kullanır; tekrarlı düğme çeşitleri azalır.
7. Mevcut işlevler, gizlilik ve saklama davranışları host regresyonlarında ve Xiaomi kabulünde bozulmaz.
8. UI/UX geliştirme tamamlandı sayılmadan önce **UX-09** son raporu ve geliştirici gerçek cihaz kabulü alınır.
9. Release sürümü, Play Store yayını, yeni izin, GPS/Remote ID uygulaması ve dış servis entegrasyonu bu roadmap'in kapsamında **değildir**.

## I. Dokümantasyon düzeni

Önerilen depo yapısı (henüz proje dosyaları üzerinde uygulanmış değildir):

```text
docs/
  uiux-redesign-roadmap.md
  uiux/
    screen-inventory.md
    design-contract.md
    component-catalog.md
    screen-state-matrix.md
    ux-00-acceptance.md
    ...
    ux-09-final-acceptance.md
```

`roadmap.md` orijinal görev ve kanıtlar için değişmez tarihsel kaynak olarak kalır. İçine gerekirse tek bir bağlantı ve tarihli "UIUX ayrı yol haritası" notu eklenir; tarihsel Phase 1–3 görevi yeniden adlandırılmaz, işaretleri değiştirilmez; **Phase 4 oluşturulmaz**.

## J. Kaynaklar ve doğrulama notu

- Android Material 3 in Compose — https://developer.android.com/develop/ui/compose/designsystems/material3
- Android Compose segmented button — https://developer.android.com/develop/ui/compose/components/segmented-button
- Android Compose accessibility / 48dp target — https://developer.android.com/codelabs/jetpack-compose-accessibility
- Android Compose edge-to-edge — https://developer.android.com/develop/ui/compose/system/setup-e2e
- Android adaptive window size classes — https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes
- WCAG 2.2 minimum text contrast — https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum

**Sınırlılık:** Bu tasarım yol haritası yedi gönderilen ekran görüntüsüne, önceki OpenCode işlevsel raporlarına ve kamuya açık Android tasarım yönergelerine dayanır; gerçek kaynak depo bu sohbetten açılıp düzenlenmemiştir. UX-00 bu nedenle ekran envanterinin koddan teyit edilmesiyle başlar. Bu dosyanın oluşturulması Android uygulamasında, Git'te veya proje kökü `roadmap.md` üzerinde hiçbir değişiklik yapmamıştır.

---

## Durum günlüğü

- **2026-11-06 — UX-00 TAMAMLANDI (doküman).** Ekran envanteri, kullanıcı yolculukları,
  bileşen kataloğu, tasarım sözleşmesi ve ekran-durum matrisi üretildi:
  `docs/uiux/screen-inventory.md`, `design-contract.md`, `component-catalog.md`,
  `screen-state-matrix.md`, `ux-00-acceptance.md`. Kaynak kodda değişiklik yok; Build Mode OFF.
  UX-01 ve sonrası BAŞLATILMADI.

- **2026-11-06 — UX-01 TAMAMLANDI (kaynak, debug-only).** `ui/theme/DesignTokens.kt`,
  `Theme.kt`, `DesignSystem.kt` (eklemeli), `DesignSystemShowcase.kt` ve 11 yeni host testi;
  ayrıntı: `docs/uiux/ux-01-design-system.md`. Host kapıları: core 370/370, app 60/60,
  lint 0 hata/15 uyarı. Ekran düzeni, gezinme, kabuk ve sistem çubuğu UX-02'ye bırakıldı;
  UX-02 ve sonrası BAŞLATILMADI.

- **2026-11-06 — UX-01 tamamlandı (kod/host).** Tasarım sistemi temeli + eksik üç bileşen
  (CompactObservationRow, ConfirmActionDialog, StateSurface) ve EmphasisButton eklendi.
  Ayrıntı: `docs/uiux/ux-01-design-system.md` §9. Xiaomi görsel kabulü AÇIK; UX-02 başlamadı.

- **2026-11-06 — UX-02 TAMAMLANDI (kod/host, DEBUG).** Global kabuk: tek kök Scaffold,
  edge-to-edge, dört sekme, birincil sekmelerde kabuk üst çubuğu, ikincil hedeflerde
  kendi başlıkları. `docs/uiux/ux-02-shell.md`. UX-03+ BAŞLATILMADI; Xiaomi görsel kabul AÇIK.

- **2026-11-06 — UX-03 TAMAMLANDI (kod/host).** Kompakt durum başlığı ve katlanır
  tetikleyici paneli: `docs/uiux/ux-03-dashboard.md`. Xiaomi görsel kabulü AÇIK.
  UX-04 ve sonrası BAŞLATILMADI.

- **2026-11-06 — UX-03 TAMAMLANDI** (kompakt panel + katlanır tetikleyiciler) · **UX-04
  TAMAMLANDI** (kompakt gözlem araç çubuğu, STALE tonu): `docs/uiux/ux-03-dashboard.md`,
  `docs/uiux/ux-04-live-observations.md`. Xiaomi görsel kabulü AÇIK. UX-05+ BAŞLATILMADI.

- **2026-11-08 — UX-05 KOD/HOST TAMAM.** Geçmiş listesi tek araç çubuğu yüzeyinde
  toplandı; yıkıcı "Tümünü sil" düşük vurgulu hata rengi eyleme indirildi (onay
  diyaloğu değişmedi). Kanıt: `docs/uiux/ux-05-history.md`. Xiaomi görsel kabulü AÇIK;
  UX-06 ve sonrası BAŞLATILMADI.

- **2026-11-08 — UX-05 TAMAMLAMA.** Satırlar açıklama nesnesinden etiket/not gösteriyor;
  tek kayıt silme eylemi hata rengiyle ayrıldı (48dp korundu). Matris:
  `docs/uiux/ux-05-history-completion.md`. Xiaomi görsel kabulü AÇIK; UX-06 BAŞLAMADI.
