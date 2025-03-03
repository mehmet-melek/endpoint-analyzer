Mimari Analizi Aracı - TypeResolver İyileştirmeleri
Bu doküman, mikro servis mimarisindeki API çağrılarını analiz eden ve raporlayan aracımızın TypeResolver bileşenine yapılan iyileştirmeleri özetlemektedir.
RequestBody ve ResponseBody Analizleri
RequestBody ve ResponseBody için farklı işleme mantıkları uyguladık. Her biri için ayrı metotlar oluşturuldu:
resolveRequestBody(Type type, boolean isValidated)
resolveResponseBody(Type type)
RequestBody Kontrolleri
RequestBody için aşağıdaki kontroller uygulanmaktadır:
@JsonIgnoreProperties(ignoreUnknown = true) Kontrolü:
DTO sınıfları üzerinde bu annotation varsa "ignoreUnknown": true olarak işaretlenir
Miras alınan sınıflarda (örn: UserDto extends HighDto) da bu kontrol yapılır
@Valid veya @Validated yoksa her zaman "ignoreUnknown": false olarak işaretlenir
İç içe nesnelerde (DTO içinde başka bir DTO) de bu kontrol yapılır
Required Kontrolü:
Bir field üzerinde @NotNull, @NotEmpty veya @NotBlank annotation'larından biri varsa, "required": true olarak işaretlenir
Bu kontroller sadece controller metodu üzerinde @Valid veya @Validated varsa etkinleştirilir
@Valid veya @Validated yoksa, tüm field'lar için "required": false olarak işaretlenir
@JsonIgnore Kontrolü:
Bir field üzerinde @JsonIgnore annotation'ı varsa, bu field JSON çıktısında gösterilmez
@JsonProperty Kontrolü:
Bir field üzerinde @JsonProperty("user_names") gibi bir annotation varsa, field adı olarak bu değer kullanılır
JPA İlişki Kontrolü:
@OneToMany, @ManyToOne, @OneToOne, @ManyToMany gibi JPA ilişki annotation'ları olan field'lar JSON çıktısında gösterilmez
Bu kontrol hem javax.persistence hem de jakarta.persistence paketleri için yapılır
Collection Tipleri:
Collection tipleri için (List, Set vb.) "type": "array" olarak işaretlenir
Collection içindeki nesneler için de yukarıdaki tüm kontroller uygulanır
Collection içindeki DTO'lar için ignoreUnknown ve required kontrolleri ayrıca yapılır
ResponseBody Kontrolleri
ResponseBody için sadece minimum bilgi sunulur:
@JsonIgnore Kontrolü:
Bir field üzerinde @JsonIgnore annotation'ı varsa, bu field JSON çıktısında gösterilmez
@JsonProperty Kontrolü:
Bir field üzerinde @JsonProperty("user_names") gibi bir annotation varsa, field adı olarak bu değer kullanılır
JPA İlişki Kontrolü:
JPA ilişki annotation'ları olan field'lar JSON çıktısında gösterilmez

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

### RequestBody Örneği

```json
"requestBody": {
  "ignoreUnknown": true,  // @JsonIgnoreProperties(ignoreUnknown = true) + @Valid/@Validated
  "items": {
    "id": {
      "type": "Long",
      "required": false
    },
    "user_names": {  // @JsonProperty("user_names")
      "type": "String",
      "required": true  // @NotNull + @Valid/@Validated
    },
    "detailDtoList": {
      "ignoreUnknown": true,  // DetailDto içinde @JsonIgnoreProperties(ignoreUnknown = true) + @Valid/@Validated
      "type": "array",
      "required": true,  // @NotNull + @Valid/@Validated
      "items": {
        "id": {
          "type": "Long",
          "required": false
        },
        "status": {
          "type": "String",
          "required": false
        }
      }
    }
  }
}
```

### ResponseBody Örneği

```json
"responseBody": {
  "items": {
    "id": {
      "type": "Long"
    },
    "orderDate": {
      "type": "String"
    },
    "status": {
      "type": "String"
    }
  }
}
```

## Önemli Noktalar

1. Miras alınan özelliklerin düzgün işlenmesi için parent sınıflar da analiz edilir
2. Field'ların tam paket adları ile kullanılan annotation'lar (örn: javax.validation.Valid) da kontrol edilir
3. @Valid veya @Validated yoksa bile ignoreUnknown değeri mutlaka false olarak belirtilir
4. Collection tipleri recursive olarak çözümlenir ve içindeki tiplerin özellikleri doğru şekilde raporlanır

Bu şekilde API dokümantasyonu için daha eksiksiz ve doğru JSON şemaları oluşturulabilir.
