# PilotBoard android app

This is the android app that hosts the dashboard web app. Currently it's just a wrapper app that makes it possible to load the web app in full screen, whereas in a web browser you can't use the full screen space.

## How to run

Load the project in Android Studio, then build and run.  
Replace the `dashboardServerAddress` with the ip and port of the server the web app is running on.  
Make sure to use the ip of the server, not "localhost", as most likely the server won't be running on the android device itself,  
but rather on your computer. For the same reason, inside the web app `index.html`, use that same ip for the websocker server connection.