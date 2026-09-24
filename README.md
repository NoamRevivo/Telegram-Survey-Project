# מערכת ניהול קהילות וסקרים בזמן אמת

בוט טלגרם + לוח בקרה ב-Java Swing: חברים מצטרפים לקהילה דרך הבוט, המנהל בונה סקר
(ידנית או באמצעות ChatGPT), הסקר נשלח לכל החברים בטלגרם, והתוצאות מתעדכנות על המסך בזמן אמת.

---

## דרישות

| רכיב | גרסה |
|---|---|
| JDK | 21 |
| Maven | 3.9+ |

---

## משתני סביבה

ב-IntelliJ הם מוגדרים ב-**Run → Edit Configurations → Environment variables**.

| משתנה | חובה | תיאור |
|---|---|---|
| `BOT_USERNAME` | כן | שם המשתמש של הבוט בטלגרם, בלי `@` |
| `BOT_TOKEN` | כן | הטוקן שהתקבל מ-@BotFather |
| `SURVEY_API_TOKEN` | לשימוש ב-ChatGPT | טוקן לשירות יצירת השאלות. בלעדיו אפשר ליצור שאלות ידנית בלבד |
| `SURVEY_API_URL` | לא | כתובת השירות. ברירת המחדל ב-`AppConfig.DEFAULT_SURVEY_API_URL` |

```bash
export BOT_USERNAME="MySurveyBot"
export BOT_TOKEN="123456:ABC-DEF..."
export SURVEY_API_TOKEN="..."
```

> שירות יצירת השאלות קורא את הטוקן מפרמטר ה-query בשם `token`, ולכן הוא נשלח שם וגם ב-header מסוג `Authorization`.
> משמעות: הטוקן עלול להירשם בלוגים של שרתים ופרוקסי בדרך — מומלץ להשתמש בטוקן ייעודי לפרויקט שאפשר לבטל.

---

## בנייה והרצה

```bash
mvn clean package          # מקמפל, מריץ את הבדיקות ובונה jar יחיד
java -jar target/Telegram-Survey-Project-1.0-SNAPSHOT.jar

mvn test                   # בדיקות בלבד
```

הרצה מתוך IntelliJ: `org.example.Main`.

---

## אופן ההרצה וההדגמה

1. הפעילו את התוכנית — נפתח לוח הבקרה ואז הבוט מתחבר לטלגרם.
2. שלחו `/start` (או «היי» / «Hi») לבוט משלושה חשבונות טלגרם לפחות —
   כל מצטרף מופיע מיד בלשונית **קהילה**.
3. בלשונית **יצירת סקר** בנו 1–3 שאלות, ידנית או דרך ChatGPT, הזינו דקות דחייה (0 = מיידי, עד 240) ולחצו **התחל סקר**.
4. השאלות נשלחות לכל חברי הקהילה עם כפתורי תשובה. לשונית **סקר פעיל**
   מציגה ספירה לאחור וטבלת התקדמות, ולשונית **תוצאות** מתעדכנת עם כל תשובה.
5. הסקר נסגר כשכולם סיימו, כשהזמן נגמר, או בלחיצה על **סיים סקר עכשיו**.
   בסגירה התוצאות ממוינות לפי שכיחות בסדר יורד.

פקודות הבוט: `/start` להצטרפות, `/help` לעזרה. הבוט מגיב בשיחה פרטית בלבד, ו-`/start abc` או `/start@BotName` מזוהות כ-`/start`.

---

## לוח זמנים של סקר

| רגע | מה קורה |
|---|---|
| T−delay | הסקר נוצר וממתין (ניתן לביטול) |
| T+0 | השאלות מופצות לכל החברים |
| אחרי ההפצה | מתחילות 5 דקות המענה |
| +3:00 | תזכורת אישית אחת בלבד, למי שטרם סיים (לכל היותר פעם אחת לכל סקר) |
| +5:00 | הסקר נסגר אוטומטית |

הערכים מוגדרים ב-`AppConfig`. משתתף שההודעות לא הגיעו אליו (חסם את הבוט — קוד 403) מסומן בלשונית **סקר פעיל** כ«לא נמסר»,
אינו חוסם סגירה מוקדמת ואינו מקבל תזכורת. תקלת רשת רגעית נוסתה שוב עם המתנה גדלה ואינה מסמנת משתתף כך.
כשהמנהל סוגר את התוכנה באמצע סקר, המשתתפים מקבלים הודעה שהסקר נסגר.

---

## בדיקות

```bash
mvn test
```

106 בדיקות יחידה המכסות את לוגיקת הסקר, התזמון וניהול הקהילה —
כולל מניעת מרוץ בין הטיק האחרון לסגירת הסקר, תזכורות למי שטרם סיים,
ביטול סקר בשלב ההמתנה, משתתף שלא ניתן להשיג, דיווח הפצה מאוחר של סקר קודם,
והפרדת הרשויות בין חברי קהילה למשתתפי סקר.
בנוסף: 300 משתתפים שעונים במקביל (900 תשובות, כל אחת נרשמת בדיוק פעם אחת),
אימות אינדקסים בתוך המנעול, טיימאאוט של סקר קודם שאינו סוגר את הסקר הבא,
פירוק תשובת ChatGPT (`SurveyJsonParser`), פירוק ה-callback (`CallbackData`),
פקודות עם פרמטר או תיוג בוט, והפצת ההודעות ב-`BotNotifier` מול שער טלגרם מדומה —
כולל כשל באמצע רצף השאלות והמשכו מהשאלה החסרה, ביטול סקר שכבר יצא, הגבלת קצב תשובות לצ'אט (`ChatThrottle`)
וניסיונות חוזרים עם המתנה גדלה ב-`TelegramGateway`,
ובניית הבקשה והטיפול בתשובות ובכשלי רשת ב-`ChatGPTService` מול לקוח HTTP מדומה.

---

## מבנה הפרויקט

```
org.example
├── ליבה        Survey · Question · SurveyParticipant · CommunityUser · SurveyState
├── ניהול       SurveyManager · CommunityManager · SurveyScheduler · Listeners<T>
├── טלגרם       TelegramGateway (SendResult) · MessageSender · TelegramBotService · BotNotifier ·
│               ChatThrottle · NamedThreadFactory · CallbackData · MessageTemplates
├── ChatGPT     ChatGPTService (HTTP) · SurveyJsonParser (פירוק בלבד) · GeneratedSurvey
├── ממשק        MainFrame · CommunityPanel · SurveyCreationPanel · ActiveSurveyPanel ·
│               SurveyStartPanel · ResultsPanel · AddQuestionDialog · GenerationLoadingCard ·
│               QuestionGenerationController · EdtSurveyListener · EdtCommunityListener ·
│               Toast · UiFactory · Dialogs · UiTheme
└── תצורה       AppConfig
```

* `SurveyManager` הוא המקור היחיד לאמת על מצב הסקר; הממשק והבוט הם מאזינים בלבד.
* כל הודעה למאזינים נשלחת מחוץ למנעול (copy-then-notify), כך שה-EDT אינו ממתין למנעול.
* כל עדכוני ה-GUI עוברים דרך `SwingUtilities.invokeLater`.