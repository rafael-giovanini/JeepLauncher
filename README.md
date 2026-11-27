# Jeep Launcher

**Interface automotiva Android desenvolvida para centrais multimídia veiculares.**

O Jeep Launcher é um aplicativo Android com interface horizontal (*landscape*), tema escuro e identidade visual automotiva. Seu objetivo é oferecer uma experiência de utilização simplificada, organizada e adequada às telas de centrais multimídia.

O projeto reúne recursos de navegação, reprodução de mídia, informações do veículo e acesso rápido a aplicativos em uma interface centralizada.

---

## Visão geral

O Jeep Launcher foi desenvolvido para transformar a tela inicial de uma central multimídia Android em um ambiente de acesso rápido às principais funções utilizadas durante a condução.

A interface prioriza:

- Organização visual e facilidade de navegação.
- Elementos dimensionados para telas automotivas.
- Acesso rápido aos aplicativos.
- Integração com sessões de mídia do Android.
- Identidade visual consistente e tema escuro.

## Funcionalidades

### Interface automotiva
- Layout horizontal adaptado a telas widescreen.
- Tema escuro com elementos de destaque em laranja.
- Painéis independentes e barra de navegação inferior.
- Relógio e informações de data.
- Organização dos recursos em áreas funcionais.

### Navegação
- Seleção de provedor de navegação.
- Integração com Google Maps.
- Integração com Waze.
- Detecção e suporte à utilização do iGO, quando instalado.
- Abertura do aplicativo de navegação selecionado.

### Reprodução de mídia
- Integração com Android MediaSession.
- Identificação de sessões de mídia ativas.
- Exibição do título e artista da faixa.
- Exibição da capa do álbum.
- Controles de reprodução, pausa, faixa anterior e próxima.
- Atualização das informações conforme a sessão de mídia.

### Acesso rápido
- Barra inferior com atalhos para as principais áreas do launcher.
- Acesso à navegação, rádio, música, telefone, Bluetooth e aplicativos.

### Informações do veículo
- Área dedicada à apresentação de informações do veículo.
- Estrutura visual preparada para futuras integrações automotivas.

> Algumas áreas ainda estão em evolução. A disponibilidade de determinados recursos depende dos aplicativos instalados, das permissões concedidas e dos recursos disponibilizados pelo dispositivo Android.

---

## Tecnologias utilizadas

- **Plataforma:** Android
- **Linguagem:** Kotlin
- **Interface:** Android XML Layouts
- **Integração de mídia:** Android MediaSession API
- **Gerenciamento de sessões:** MediaSessionManager e NotificationListenerService
- **Build:** Gradle
- **Ambiente de desenvolvimento:** Android Studio

## Requisitos

Para desenvolver e compilar o projeto, recomenda-se:

- Android Studio.
- JDK compatível com a versão do Gradle utilizada pelo projeto.
- Android SDK configurado.
- Git.
- Dispositivo Android ou emulador para testes.

O aplicativo possui configuração de `minSdk` 23 na versão atualmente compilada. A compatibilidade efetiva também depende das APIs utilizadas e dos aplicativos externos integrados.

## Como obter o projeto

Clone o repositório:

```bash
git clone https://github.com/rafael-giovanini/JeepLauncher.git
```

Entre na pasta:

```bash
cd JeepLauncher
```

Abra o projeto no Android Studio e aguarde a sincronização do Gradle.

## Como compilar

No Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

O APK de depuração será gerado em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Para instalar em um dispositivo conectado via ADB:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Estrutura do projeto

```text
JeepLauncher/
├── app/
│   └── src/
│       └── main/
│           ├── java/
│           │   └── br/com/rgservices/jeeplauncher/
│           │       ├── media/
│           │       ├── navigation/
│           │       ├── ui/
│           │       └── MainActivity.kt
│           ├── res/
│           │   ├── drawable/
│           │   ├── layout/
│           │   └── values/
│           └── AndroidManifest.xml
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

## Permissões e integrações

Algumas funcionalidades dependem de permissões específicas do Android.

A integração com sessões de mídia, por exemplo, depende da autorização de acesso às notificações para que o sistema possa identificar e acompanhar os players ativos.

Os aplicativos externos de navegação precisam estar instalados e disponíveis no dispositivo.

## Estado do desenvolvimento

O Jeep Launcher está em desenvolvimento incremental.

As prioridades incluem:

- Aprimoramento da interface e experiência de utilização.
- Validação em diferentes resoluções de centrais multimídia.
- Evolução dos recursos de navegação.
- Melhorias na integração com aplicativos de mídia.
- Desenvolvimento de futuras integrações com informações do veículo.

## Repositório

Código-fonte:  
https://github.com/rafael-giovanini/JeepLauncher

## Autor

**Rafael Giovanini Santana**

Desenvolvimento e manutenção do Jeep Launcher.

---

*Jeep Launcher — uma interface automotiva Android desenvolvida para centralizar recursos e simplificar a experiência nas centrais multimídia.*