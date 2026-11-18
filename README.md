# Claude Pet — Android Shimeji

Native Android desktop companion with a draggable floating pet, gravity-to-bottom behavior, pixelated movement animation, and add/remove controls.

The app requests Android's **Display over other apps** permission only when the user taps Add. The pet image is loaded from the supplied image URL on first launch; if that temporary link has expired, choose the photo from the device with **Змінити фото**.

## Build

GitHub Actions builds `Claude_Pet.apk` on pushes to `main` and on manual workflow runs. Download it from the workflow's Artifacts section.

## Run

1. Install the APK.
2. Open the app and allow Display over other apps.
3. Wait for the source photo to load, or select it from the gallery.
4. Tap **Додати**. Drag the character anywhere; release it above the bottom edge and it falls down, then crawls along the bottom. Tap **Прибрати** to remove the most recently added pet.
