# Endpoint Analyzer

Bu araç, Java Spring projeleri için REST endpoint'lerini ve Feign client çağrılarını analiz eder.

## Kapsam

### Type Resolution
- **Primitive Types**: Java primitive tipleri ve wrapper sınıfları (`int`, `long`, `String` vb.)
- **Collection Types**: 
  - List ve Set koleksiyonları
  - Generic tip desteği (`List<String>`, `Set<CustomType>` vb.)
  - Array notasyonu (`array<String>`, `array<CustomType>`)
- **Map Types**: Key-Value çiftleri için özel format (`Map<String,Integer>`)
- **Date/Time Types**: Java tarih/zaman tiplerinin standardizasyonu
  - `java.util.Date` -> "Date"
  - `java.time.LocalDateTime` -> "DateTime"
  - `java.time.LocalDate` -> "Date"
  - `java.sql.Timestamp` -> "DateTime"

### JSON Annotations
- **@JsonIgnore**: JSON serializasyonundan hariç tutulan alanların tespiti
- **@JsonProperty**: Özel alan isimlendirmelerinin desteklenmesi
  - `@JsonProperty("user_name")` -> "user_name" olarak çıktıya eklenir
- **@Transient**: JPA/Jakarta Persistence transient alanlarının tespiti

### Enum Handling
- Enum sabitleri için özel tip çözümleme
- **@JsonValue** annotation desteği
- getValue/value metod desteği
- Enum değerlerinin tip tespiti (string, integer, vb.)

### JPA Relations
- JPA ilişki annotationlarının tespiti:
  - @OneToMany
  - @ManyToOne
  - @OneToOne
  - @ManyToMany
- İlişkili alanların "relation" olarak işaretlenmesi

### Response Types
- **Void Responses**: 
  - `void` dönüş tipleri
  - `ResponseEntity<Void>`
  - Boş response body tespiti
- **Generic ResponseEntity**: `ResponseEntity<T>` için generic tip çözümleme

### Request/Response Parameters
- Path variables (`@PathVariable`)
- Query parameters (`@RequestParam`)
- Request body mapping (`@RequestBody`)
- Response body type resolution

### Logging
- INFO seviyesinde anlamlı loglar
- Hata durumlarında detaylı error logları
- Debug loglarının konfigürasyon ile kontrolü

### Error Handling
- Type resolution hataları için fallback mekanizması
- Dosya okuma/yazma hataları için error handling
- Parser hataları için uygun error mesajları

### Output Format
- Boş alanlar için null kullanımı (empty map yerine)
- Nested object desteği
- Array ve koleksiyon tipleri için özel format
- İlişkisel verilerin standardize edilmiş gösterimi

### Spring REST Controllers
- **@RestController/@Controller Detection**:
  - @RestController annotation kontrolü
  - @Controller + @ResponseBody kombinasyonu desteği
- **Request Mapping Varyasyonları**:
  - @RequestMapping
  - @GetMapping
  - @PostMapping
  - @PutMapping
  - @DeleteMapping
  - @PatchMapping
- **Path Resolution**:
  - value attribute
  - path attribute
  - direkt annotation değeri
  - Class ve method seviyesinde path birleştirme

### Feign Client Detection
- **@FeignClient Attribute Önceliği**:
  1. name attribute
  2. value attribute
  3. url attribute
  4. Bulunamazsa "unknown-application"
- **Path Resolution**:
  - @FeignClient path attribute
  - @RequestMapping kombinasyonu
  - Base URL ve method path'lerinin birleştirilmesi

### HTTP Method Resolution
- **Method Annotation Önceliği**:
  1. Özel HTTP method annotationları (@GetMapping, @PostMapping vb.)
  2. @RequestMapping method attribute
  3. Default olarak "GET"
- **RequestMapping Method Attribute**:
  - RequestMethod enum değerlerinin parse edilmesi
  - "RequestMethod." prefix'inin temizlenmesi

### Path Parameter Resolution
- **@PathVariable Varyasyonları**:
  - name attribute
  - value attribute
  - Annotation değeri
  - Parametre ismi (fallback)
- **@RequestParam Varyasyonları**:
  - name attribute
  - value attribute
  - required attribute kontrolü
  - Parametre ismi (fallback)

### Client Name Resolution
- **Service İsmi Tespiti**:
  1. @FeignClient name attribute
  2. @FeignClient value attribute
  3. @FeignClient url attribute
  4. Sınıf ismi
  5. "unknown-application" (fallback)
- **URL Pattern Desteği**:
  - HTTP/HTTPS URL kontrolü
  - Domain name extraction
  - Path parametrelerinin temizlenmesi
