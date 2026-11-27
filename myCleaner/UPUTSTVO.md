# Čistač telefona — Uputstvo

## 1. Pravljenje APK-a bez programiranja (GitHub Actions — preporučeno)

1. Napravi nalog na [github.com](https://github.com) ako ga nemaš.
2. Klikni na **+** → „New repository", daj mu naziv (npr. `cistac`) i klikni „Create repository".
3. Klikni **uploading an existing file**, prevuci ceo folder projekta, popuni poruku i klikni „Commit changes".
4. Idi na **Actions** tab u repozitorijumu.
5. Ako ti traži da odobrish Workflow — klikni „I understand my workflows, go ahead and enable them".
6. Vidiš „Build APK" u listi. Klikni na njega → „Run workflow" → „Run workflow".
7. Sačekaj 3–5 minuta. Kad postane zeleno, klikni na taj run → skroluj dole → „Artifacts" → preuzmi `CistacTelefona-debug.zip`.
8. Raspakuj, nađi `app-debug.apk`, prebaci na telefon i instaliraj.

---

## 2. Pravljenje APK-a u Android Studio (lokalno)

### Instalacija (jednom)
1. Preuzmi **Android Studio** sa [developer.android.com/studio](https://developer.android.com/studio).
2. Instaliraj — tokom instalacije prihvati podrazumevane opcije (SDK Manager će sve preuzeti).

### Build
1. `File → Open` → otvori folder `CistacTelefona`.
2. Sačekaj da Gradle preuzme zavisnosti (traje 5–10 min prvi put, potreban internet).
3. `Build → Build Bundle(s) / APK(s) → Build APK(s)`.
4. Klikni na „locate" u notifikaciji ili nađi fajl u `app/build/outputs/apk/debug/app-debug.apk`.
5. Pošalji APK na telefon (email, USB, cloud) i instaliraj ga.

---

## 3. Podešavanje dozvola na telefonu (posle instalacije)

### 3.1 Pristup korišćenju — OBAVEZNO za liste i bateriju
Na početnom ekranu aplikacije tapni „Dozvoli pristup korišćenju".
Nađi „Čistač telefona" u listi → Omogući.

### 3.2 Pristup svim fajlovima — za brisanje temp fajlova
Na početnom ekranu tapni „Dozvoli pristup svim fajlovima" → Dozvoli.

### 3.3 USB debagovanje (samo za ADB alat)
Podešavanja → O telefonu → kucni 7x na „Broj verzije" → Programerske opcije → USB debagovanje.

---

## 4. Windows ADB alat — uklanjanje sistemskog bloatware-a

### Instalacija ADB-a
1. Preuzmi [Platform Tools](https://developer.android.com/studio/releases/platform-tools).
2. Raspakuj, npr. u `C:\adb`.
3. Dodaj `C:\adb` u Windows PATH: Sistem → Napredne postavke → Promenljive okruženja → System variables → Path → Edit → New → `C:\adb`.

### Korišćenje
1. Priključi telefon USB kablom (odaberi „Prenos fajlova").
2. Na telefonu prihvati upit „Dozvoli USB debagovanje za ovaj računar".
3. Dvaput klikni na `adb_bloatware.bat`.
4. Koristi meni — opcija 3 čita `paketi.txt` i brise sve navedene pakete.

### Pravljenje paketi.txt
- Opcija 1 ili 2 u meniju izlistava pakete.
- Ili: u Android aplikaciji → Neiskorišćene aplikacije → „Kopiraj za ADB" → nalepi u `paketi.txt`.

### Sigurnost
- Sistemske aplikacije se brišu samo za tvog korisnika (`--user 0`) — može se oporaviti opcijom 4.
- Korisničke aplikacije se brišu trajno.
- Pre brisanja napravi backup bitnih podataka.

---

## 5. Šta aplikacija može i ne može

| Funkcija | Aplikacija | ADB alat |
|---|---|---|
| Lista nekorišćenih app (30+ dana) | ✅ | ❌ |
| Deinstalacija korisničkih app | ✅ uz potvrdu | ✅ |
| Uklanjanje sistemskog bloatware-a | ❌ | ✅ |
| Keš aplikacija (prikaz veličine) | ✅ | ❌ |
| Brisanje keša direktno | ❌ Android 11+ | ✅ |
| Brisanje temp fajlova | ✅ uz potvrdu | ✅ |
| Baterija: mA punjenja/pražnjenja | ✅ | ❌ |
| Procena vremena do punjenja | ✅ | ❌ |
| Lista potrošača baterije | ✅ procena | ❌ |
| Gašenje pozadinskih procesa | ✅ | ✅ |
| Auto-gašenje posle 10 min | ✅ WorkManager | ❌ |

---

## 6. Često postavljana pitanja

**Ne vidi aplikacija moj telefon (ADB)?**
Provjeri: Podešavanja → Programerske opcije → USB debagovanje = UKLJUČENO.
Zatim: `adb devices` u CMD — ako piše „unauthorized", prihvati RSA upit na telefonu.

**„Pristup korišćenju" ne radi?**
Neke ROM-ove (Xiaomi/MIUI) traže i „Pristup pozadinskim aktivnostima" u sigurnosnim postavkama.

**APK se ne instalira?**
Podešavanja → Aplikacije → Poseban pristup aplikacija → Instaliraj nepoznate aplikacije → odaberi browser/Files i dozvoli.
