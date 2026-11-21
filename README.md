# Scanner by Numio

An offline document and QR scanner for Android, with a calm "folded paper" look.
No internet permission, no ads, no tracking. Your scans never leave your phone.

> Work in progress. Not released yet.

## Features

- **Scan documents** with the camera. Scanner finds the page edges for you, and you can drag the four corners to fix them.
- **Straighten pages**: crooked photos are turned into flat, straight pages.
- **Filters**: Clean paper (removes shadows), Sharp text, B&W, Colour pop, Greyscale, Warm, Cool, Sepia and Night.
- **Adjust** brightness and contrast, and rotate pages.
- **Multi-page PDFs**: save to your files or share with any app.
- **Import from Gallery**: turn photos you already took into clean scans.
- **QR codes and barcodes**: links are shown in full and only open when you tap them. Wi-Fi codes show the network and password.
- **Light, dark or follow system** theme.

### Coming next

- Text from scans (English and Malayalam), fully offline
- Search inside your scans
- ID card mode (front and back on one page)
- Signing PDFs

## Privacy

Scanner asks for one permission: **camera**. It has no internet permission, so it can't send anything anywhere.
Scans are stored in the app's private storage, and you choose what to save or share.
Every build is checked to make sure the internet permission never sneaks in.

## Built with

Kotlin, Jetpack Compose, CameraX, [ZXing](https://github.com/zxing/zxing) (Apache-2.0) and Android's own PDF maker.

## About

Scanner is part of the [Numio](https://getnumio.org) family of open-source Android apps.

Built by RR with help from Claude.
