# Endpoint Analyzer Projesi Plan

## 1. Proje Yapısı
```java
endpoint-analyzer/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── ykb/
        │           └── architecture/
        │               └── analyzer/
        │                   ├── Application.java
        │                   ├── core/
        │                   │   ├── model/
        │                   │   │   ├── endpoint/
        │                   │   │   │   ├── BaseEndpoint.java
        │                   │   │   │   ├── ProvidedEndpoint.java
        │                   │   │   │   └── ConsumedEndpoint.java
        │                   │   │   ├── method/
        │                   │   │   │   ├── EndpointMethod.java
        │                   │   │   │   ├── MethodParameter.java
        │                   │   │   │   └── HttpMethod.java
        │                   │   │   └── report/
        │                   │   │       ├── ServiceReport.java
        │                   │   │       └── AnalysisReport.java
        │                   │   └── exception/
        │                   │       ├── AnalyzerException.java
        │                   │       └── ParserException.java
        │                   ├── parser/
        │                   │   ├── base/
        │                   │   │   ├── AbstractEndpointParser.java
        │                   │   │   └── ParserStrategy.java
        │                   │   ├── provider/
        │                   │   │   └── RestControllerParser.java
        │                   │   ├── consumer/
        │                   │   │   ├── FeignClientParser.java
        │                   │   │   └── RestTemplateParser.java
        │                   │   └── util/
        │                   │       ├── AnnotationParser.java
        │                   │       ├── TypeResolver.java
        │                   │       └── PathResolver.java
        │                   ├── service/
        │                   │   ├── AnalyzerService.java
        │                   │   └── ReportGeneratorService.java
        │                   ├── config/
        │                   │   ├── AnalyzerConfig.java
        │                   │   └── ParserConfig.java
        │                   └── output/
        │                       ├── formatter/
        │                       │   ├── OutputFormatter.java
        │                       │   ├── JsonFormatter.java
        │                       │   └── YamlFormatter.java
        │                       └── writer/
        │                           ├── OutputWriter.java
        │                           └── FileOutputWriter.java
        └── resources/
            └── application.yml
```

Bu yapı, orijinal yapıya göre şu avantajları sağlar:

1. **Daha İyi Modülerlik**: Core modeller ve parser'lar daha iyi ayrılmış durumda
2. **Genişletilebilirlik**: Yeni parser tipleri kolayca eklenebilir
3. **Daha Net Sorumluluklar**: Her paket kendi sorumluluğuna odaklanıyor
4. **Esnek Output**: Farklı çıktı formatları için hazır altyapı
5. **Test Edilebilirlik**: Bağımlılıklar interface'ler üzerinden yönetiliyor

İsterseniz bu plan doğrultusunda spesifik sınıfların implementasyonlarına geçebiliriz. Hangi kısımdan başlamak istersiniz?

## 2. Temel Bileşenler ve Sorumlulukları

### 2.1 Core Katmanı
- **model**: Domain modellerini içerir
  - **endpoint**: Endpoint tanımlamaları
  - **method**: HTTP method ve parametre bilgileri
  - **report**: Analiz sonuçları için model sınıfları

### 2.2 Parser Katmanı
- **base**: Temel parser altyapısı ve strateji deseni
- **provider**: Controller parser implementasyonları
- **consumer**: Client parser implementasyonları
- **util**: Parser yardımcı sınıfları

### 2.3 Service Katmanı
- **AnalyzerService**: Parser orchestration
- **ReportGeneratorService**: Analiz sonuçlarının raporlanması

### 2.4 Output Katmanı
- **formatter**: Çıktı format dönüşümleri (JSON, YAML)
- **writer**: Dosya yazma işlemleri

## 3. Geliştirme Adımları

1. Core modellerin oluşturulması
2. Parser altyapısının kurulması
3. RestController parser implementasyonu
4. FeignClient parser implementasyonu
5. Analiz servisinin geliştirilmesi
6. Output formatters implementasyonu
7. Test coverage sağlanması
8. Dokümantasyon

## 4. Teknik Detaylar

### 4.1 Kullanılacak Kütüphaneler
- JavaParser: Java kaynak kodu analizi
- Jackson: JSON işlemleri
- Spring Boot: Temel framework
- Lombok: Boilerplate azaltma
- JUnit 5: Test framework

### 4.2 Önemli Tasarım Prensipleri
- Strategy Pattern: Parser implementasyonları için
- Factory Pattern: Parser oluşturma
- Builder Pattern: Report oluşturma
- Interface Segregation: Modüler parser yapısı
- Open/Closed Principle: Yeni parser tipleri için genişletilebilir yapı
