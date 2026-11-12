# Class Board — Android app

A native Android app that keeps your class timetables and alerts you
before each class. Alerts are scheduled directly with your phone's own
alarm system: no server, no internet, and they keep working when the
app is closed or the phone has been restarted.

## What's in it

- **Multiple schedules.** Each schedule (a semester, a summer term, a
  work rota) is its own tab along the top. Tap **+ New** to add one;
  long-press a tab, or use the ⋯ button, to rename, duplicate or
  delete it. A schedule can have its alerts switched off without
  deleting it.
- **Weekly timetable.** Every schedule is drawn as a table — days
  across, hours down, one coloured block per class. Classes that
  overlap sit side by side. Switch to **List** for the same classes
  day by day. Tap any class to edit it.
- **Next up.** The card at the top shows the class that's on now or
  coming next, with a live countdown.
- **Flexible alerts.** Two settings, under the tune icon:
  - **Start alerting** — how many minutes before a class the first
    alert arrives (1–180).
  - **Repeat every** — how often it comes back until the class starts
    (or Off for a single alert).

  For example, 15 minutes and every 5 gives alerts at 15, 10 and 5
  minutes before, plus one when the class starts.
- **Seven colour themes**, and **backup** to a file (export saves
  every schedule; import adds a file's schedules as new tabs).

The app starts empty. There is no sample data — you create a schedule
and add your own classes.

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

1. Open the app and tap **Create a schedule**. Give it a name.
2. Tap **Add class**. Pick every day the class meets — it's added to
   each of them in one go.
3. Open settings (the tune icon, top right) and tap **Turn on
   alerts**. This asks for notification access and, on newer Android
   versions, permission to schedule exact alarms. Say yes to both.
4. Set **Start alerting** and **Repeat every** to suit you.

That's it. Closing the app, restarting your phone, or not opening it
for weeks won't stop the alerts.

## Updating from the earlier version

Installing this over the previous single-schedule version keeps your
classes: they appear as one schedule, named after the semester you had
entered. The To-Do list is gone, and its saved items are removed.

## Where things are in the code

| File | What it does |
| --- | --- |
| `MainActivity.kt` | The one screen: home page and settings page |
| `Dialogs.kt` | Add/edit class, add/edit schedule, confirmations |
| `TimetableView.kt` | Draws the weekly timetable |
| `Ui.kt`, `Themes.kt` | Colours, themes, shared drawing helpers |
| `ScheduleStore.kt` | Saving and loading schedules, settings, backups |
| `Schedule.kt`, `ScheduleEntry.kt` | A schedule, and one class in it |
| `ScheduleMath.kt` | Time arithmetic: next class, alert times, table layout |
| `AlarmScheduler.kt` | Hands alert times to Android's alarm system |
| `ReminderReceiver.kt`, `ClassForegroundService.kt`, `NotificationHelper.kt` | Showing the alert when an alarm fires |
| `BootReceiver.kt` | Re-arms alerts after a restart, update or clock change |
