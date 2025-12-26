# Telegram Live (Android, no server)

אפליקציה לאנדרואיד שמתחברת לטלגרם דרך TDLib (התחברות עם מספר+קוד נשמרת מקומית),
מציגה הודעות בלייב, מציגה Thumbnail, מאפשרת לבחור מלבני טשטוש במסך מגע,
מעבדת את המדיה מקומית עם FFmpegKit, ושולחת לערוץ ציבורי.

## 1) חובה: API_ID + API_HASH
1. כנס ל- my.telegram.org
2. API Development Tools
3. צור אפליקציה וקבל `api_id` ו-`api_hash`
4. פתח `app/build.gradle` וחליף:
   - TG_API_ID = המספר שלך
   - TG_API_HASH = המחרוזת שלך

> לא מכניסים את זה לתוך "קוד באינטרנט" אלא לתוך הפרויקט לפני קומפילציה.

## 2) קומפילציה אונליין ל-APK (GitHub Actions)
אחרי שתעלה את הפרויקט ל-GitHub, כל Push ל-main יבנה APK אוטומטית.
- Actions -> Build APK -> Artifacts -> app-debug-apk -> הורדה

## 3) העלאה ל-GitHub מהאייפון (הכי קל)
GitHub עצמו לא מעלה תיקיות בצורה נוחה מהאייפון.
הדרך הכי חלקה:

### אופציה A (מומלץ): אפליקציית Working Copy (iOS)
1. התקן "Working Copy" מה-App Store
2. העתק/ייבא את התיקייה (או ZIP) ל-Working Copy:
   - Files -> לחץ על ה-ZIP -> Share -> "Copy to Working Copy"
   - אם הגיע כ-ZIP, תחלץ אותו בתוך Working Copy (Unzip)
3. Create new repository ב-Working Copy
4. Commit
5. Connect ל-GitHub (Account)
6. Push ל-repo חדש

### אופציה B: דרך אתר GitHub (לא תמיד שומר תיקיות טוב)
1. פתח github.com בדפדפן -> New repository
2. Add file -> Upload files
3. תצטרך להעלות קבצים רבים ידנית (לא כיף). ZIP לא מתפרק אוטומטית.

## 4) שימוש באפליקציה
- הפעלה -> התחברות עם טלפון -> קוד -> (אם יש) 2FA
- במסך העליון: הזן @channel ציבורי -> שמור
- בחר הודעה -> הורד Thumbnail -> סמן מלבנים -> הורד מדיה -> החל טשטוש -> שלח לערוץ

## הערות
- התרגום משתמש ב-LibreTranslate ציבורי (חינם). אפשר להחליף URL בקוד (Translate.kt).
- אם תלות TDLib מה-Gradle לא נבנית אצלך – תגיד לי, ואתן גרסה חלופית עם jniLibs מובנים.
