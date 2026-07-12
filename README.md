# Class Board — Android app

This is a real, native Android app (not a website wrapped up) that
schedules reminders directly with your phone's own alarm system. No
server, no internet needed once it's installed — it works exactly like
setting an alarm clock, just automatically, for every class every week.

## How to get the installable file (.apk)

You don't need Android Studio or a computer for this — GitHub will
build it for you automatically, for free.

1. Create a new GitHub repository (e.g. "ClassBoard-Android") and
   upload every file and folder from this project into it, keeping the
   same folder structure (the `.github` folder included — it might be
   hidden in your file browser, but make sure it comes along).
2. Go to the **Actions** tab on that repository.
3. You should see a workflow called "Build Class Board APK" running
   automatically (it starts the moment you upload the files). If it
   doesn't start on its own, click **Build Class Board APK** on the
   left, then **Run workflow**.
4. Wait a few minutes for it to finish — it'll show a green checkmark
   when done.
5. Click into that finished run, scroll to the bottom, and you'll see
   an artifact called **class-board-apk** — tap it to download a zip
   containing `app-debug.apk`.
6. Unzip that on your phone (or transfer the .apk directly), tap it to
   install. Android will warn about installing from an unknown source
   the first time — that's normal for anything not from the Play
   Store; allow it.

## First-time setup on your phone

1. Open the app.
2. Tap **Enable notifications** — this asks for two permissions:
   notification access, and (on newer Android versions) permission to
   schedule *exact* alarms. Say yes to both.
3. Tap **Add a class** to build out your schedule, or edit the sample
   ones already there.
4. That's it — reminders are now scheduled directly with Android
   itself. Closing the app, restarting your phone, or not opening it
   for weeks won't stop them.

## Each new semester

Open the app, delete last semester's classes (or edit them in place),
add the new ones. Everything reschedules itself automatically.
