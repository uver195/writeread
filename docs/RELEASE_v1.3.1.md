# v1.3.1

English model upgraded: retrained on EMNIST Letters (124,800 real handwritten samples).
Accuracy on real handwriting: 93.88%. Same features as v1.3.0, better English recognition.

## Download

| File | Size | SHA-256 |
| --- | --- | --- |
| v1.3.1-release.apk | 31.9MB | D842E181E430FFC12F211D673E7BB58AB4A99CC98705DAE434AF6798231EDA0F |
| v1.3.1-debug.apk | 32.5MB | E0F2898A2E6936A98F00AA248DF42B74B3FFC92B3FB3C19F0B19C9B2F60D9FA9 |

## What changed from v1.3.0

- English letter model retrained with EMNIST real handwritten data (124,800 samples)
- 93.88% accuracy on real handwriting test set
- Better generalization for finger-written letters on phone screen
- Model size unchanged (~804KB), Chinese recognition unchanged

## All features

- Chinese handwriting recognition (3755 GB2312 chars, ~95% accuracy)
- English letter recognition (26 letters A-Z, ~94% accuracy on real handwriting)
- Dual-model cascade: picks higher confidence result
- Auto word formation and TTS
- Low-confidence prompt: please rewrite
- Fully offline, no network, no ads, no data collection
