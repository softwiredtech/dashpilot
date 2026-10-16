# Dash App Contest

Build the dashboard people look at while they drive. Two categories, one winner each, **$250 per winner**, paid out by Softwired Technologies Ltd.:

- **Web**: HTML, CSS and JavaScript
- **Rive**: a `.riv` file

| | |
|---|---|
| Submissions open | October 16 |
| Deadline | October 30, 23:00 GMT |
| Winners announced | November 12, on the [DashKit Discord](https://discord.gg/naTZgwVqvA) and [@softwiredtech](https://x.com/softwiredtech) on X |

## Getting started

Part of the challenge is finding your way around this repo. Start with the [dash-apps README](../README.md): it defines every value your dashboard receives, and how web and Rive dashboards get them. The existing dashboards in [`dash-apps/`](../) are working examples. [`rive/dash-demo/`](../rive/dash-demo/) is a complete Rive one.

## Try it in the app

Install DashPilot from [Google Play](https://play.google.com/store/apps/details?id=com.softwiredtech.dashpilot) or [TestFlight](https://testflight.apple.com/join/hXTwZmh5). On the home screen, set the data source to **Demo**: it plays a loop of simulated driving, so you don't need a car.

- **Rive:** Settings → Developer → **Load Rive File**.
- **Web:** Settings → Developer → **Load dash app from URL**, then enter the address of your page served from your computer (e.g. `http://192.168.1.20:8000`).

Test both on a phone in landscape before you submit.

## Submitting

Open a pull request to this repo titled **`[dash contest] My dash app`** (with your app's name). It should add one folder and nothing else:

```
dash-apps/contest/<github-username>-<app-name>/
  README.md     # name, category (web or rive), one paragraph about it, credits for any assets
  preview.jpg   # landscape screenshot, ~2:1
  ...           # web: index.html and its files / rive: the .riv, plus its source if you have one
```

Make a **screen recording** (30–60 s) of your dashboard running in DashPilot with the Demo data source. Attach it to the PR description, and post it with a link to your PR in **#hmi-challenge** on the [DashKit Discord](https://discord.gg/naTZgwVqvA).

One entry per person. You can push fixes to your PR until the deadline.

## Rules

- Landscape, readable at a glance while driving: the speed is the most legible thing on screen.
- Works offline: no network requests, everything ships in your folder.
- Web: 10 MB max, and works on both Android and iOS. The README explains the difference.
- Rive: 5 MB max, built on the `DashViewModel` described in the README.
- Your own work. Entries are contributed under this repo's [MIT license](../../LICENSE) so we can ship them.
- **Assets are your responsibility.** Every font, image, icon and sound must be yours or licensed for redistribution in an MIT project. List each one with its source and license in your README. Entries with unlicensed assets are disqualified.

## Judging

Winners are picked by the DashPilot team. We look at:

- **Readability:** can the driver read the speed and other key info with a quick glance, without taking their eyes off the road for long?
- **Creativity:** does it look and feel different from the dashboards DashPilot already has?
- **Use of car data:** does it show more than the speed? Think blinkers, blind spots, gear, driver assist and battery. See the [CarState](../README.md#carstate) for everything you get.
- **Popularity:** social media shares, likes, etc.

Winners and runners-up are merged and shipped in DashPilot with credit. We'll contact winners by email to arrange payment.
