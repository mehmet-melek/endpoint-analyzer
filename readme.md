# Mimari Analizi Aracı

Bu araç, mikro servis mimarisindeki API çağrılarını analiz eder ve detaylı bir rapor oluşturur. Hem sağlanan (provided) hem de tüketilen (consumed) endpointleri tespit eder.

## Temel Özellikler

### 1. Request/Response Analizi
- RequestBody ve ResponseBody için ayrı işleme mantıkları
- Validation kontrollerinin tespiti
- JSON annotation'larının işlenmesi
- Collection tiplerinin çözümlenmesi

### 2. RequestBody İşleme Özellikleri

#### 2.1 JSON Annotation Kontrolleri
- **@JsonIgnoreProperties(ignoreUnknown = true)**:
  - DTO sınıfı üzerinde varsa `"ignoreUnknown": true` olarak işaretlenir
  - Miras alınan sınıflarda kontrol edilir
  - İç içe nesnelerde (nested DTOs) kontrol edilir
  - `@Valid` veya `@Validated` yoksa `"ignoreUnknown": false` olarak işaretlenir

#### 2.2 Validation Kontrolleri
- **Required Field Tespiti**:
  - `@NotNull`, `@NotEmpty`, `@NotBlank` annotation'ları kontrol edilir
  - Controller metodu üzerinde `@Valid` veya `@Validated` varsa etkinleştirilir
  - Validation yoksa tüm field'lar `"required": false` olarak işaretlenir

#### 2.3 Field İşleme
- **@JsonIgnore**: Field JSON çıktısından çıkarılır
- **@JsonProperty**: Özel field isimlendirmesi sağlanır
- **JPA İlişkileri**: `@OneToMany`, `@ManyToOne`, `@OneToOne`, `@ManyToMany` ilişkileri işlenir

#### 2.4 Collection Tipleri
- List, Set gibi collection'lar için `"type": "array"` formatı
- Generic tiplerin çözümlenmesi
- Nested DTO'ların işlenmesi

### 3. ResponseBody İşleme Özellikleri

ResponseBody için minimal bilgi sunulur:
- **@JsonIgnore** kontrolü
- **@JsonProperty** desteği
- JPA ilişkilerinin filtrelenmesi
- Collection tiplerinin standardizasyonu

### 4. Tip Çözümleme (Type Resolution)

#### 4.1 Temel Tipler
- Java primitive tipleri (`int`, `long`, `String` vb.)
- Wrapper sınıfları
- Tarih/zaman tipleri standardizasyonu

#### 4.2 Complex Tipler
- Generic tipler
- Collection tipleri
- Custom sınıflar
- Enum sabitleri

#### 4.3 Özel Durumlar
- Void dönüş tipleri
- ResponseEntity wrapper'ları
- Optional tipleri

## Önemli Noktalar

1. Miras alınan özelliklerin düzgün işlenmesi için parent sınıflar da analiz edilir
2. Field'ların tam paket adları ile kullanılan annotation'lar (örn: javax.validation.Valid) da kontrol edilir
3. @Valid veya @Validated yoksa bile ignoreUnknown değeri mutlaka false olarak belirtilir
4. Collection tipleri recursive olarak çözümlenir ve içindeki tiplerin özellikleri doğru şekilde raporlanır

Bu şekilde API dokümantasyonu için daha eksiksiz ve doğru JSON şemaları oluşturulabilir.

### Unresolved Types Handling

TypeResolver, bir tipi çözümleyemediğinde özel bir format kullanır:

```json
{
  "requestBody": {
    "_unresolved": true,
    "_reason": "Could not resolve type: CustomRequest"
  }
}
```

Bu format şu durumlarda kullanılır:

1. **Request Body Çözümlenemediğinde**:
   - Sınıf bulunamadığında
   - Generic tip çözümlenemediğinde
   - Sembol çözümleyici hatası olduğunda

2. **Response Body Çözümlenemediğinde**:
   - ResponseEntity içindeki tip bulunamadığında
   - Return tipi çözümlenemediğinde
   - Complex tipler çözümlenemediğinde

3. **Collection Tipleri İçin**:
   - List/Set içindeki generic tip bulunamadığında
   - Nested objeler çözümlenemediğinde

### Query Parameters

1. **Provided Endpoints (Controllers)**:
   ```json
   "queryParameters": {
     "status": {
       "type": "String",
       "required": true/false
     }
   }
   ```

   Required flag'i şu durumlarda `true` olur:
   - `@RequestParam(required = true)` kullanıldığında
   - Class level `@Valid` veya `@Validated` + `@NotNull`/`@NotEmpty`/`@NotBlank` kombinasyonunda
   - Method level `@Valid` veya `@Validated` + `@NotNull`/`@NotEmpty`/`@NotBlank` kombinasyonunda
   - Parameter level `@Valid` veya `@Validated` + `@NotNull`/`@NotEmpty`/`@NotBlank` kombinasyonunda

2. **Consumed Endpoints (Feign Clients)**:
   ```json
   "queryParameters": {
     "status": "String"
   }
   ```
   - Feign client'larda sadece parametre adı ve tipi tutulur
   - Required kontrolü yapılmaz

### Client Name Resolution

Feign client'lar için isim çözümleme önceliği:

1. `@FeignClient(name = "...")` 
2. `@FeignClient(value = "...")`
3. `@FeignClient(url = "...")`
4. `@FeignClient` tek değer
5. Bulunamazsa "unknown-application"

Parametrik değerler için (`${...}`):
- application.yml/properties'den değer çözümlenir
- Çözümlenemezse orijinal değer kullanılır
- Case-insensitive arama yapılır

### Client Grouping

Aynı client name'e sahip farklı Feign interface'leri tek bir client altında gruplanır:

```json
{
  "consumedEndpoints": [
    {
      "clientApplicationName": "service.example",
      "clientOrganizationName": "service",
      "clientProductName": "service.example",
      "apiCalls": [
        // Tüm ilgili interface'lerden gelen API çağrıları
      ]
    }
  ]
}
```

Bu sayede:
- Aynı servise ait tüm çağrılar bir arada görünür
- Farklı paketlerdeki aynı servis çağrıları birleştirilir
- Daha organize bir API dokümantasyonu sağlanır
