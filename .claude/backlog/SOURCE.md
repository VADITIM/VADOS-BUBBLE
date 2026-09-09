Do each step by step and I will report back on issues and fixes needed for current scope until its perfect and meets all my standards with fluidity, dynamic, interaction, response and feeling 
The Dynamic Bubble is supposed to be a system wide addition that is supposed to enchance notifications and interactions 

[] All bubbles should become bouncy as the lock now bubble is. Look at its animations and interactions and adapt it system wide, save also to rules

[] bubble change swipe animation has become bugged: Instead of only the icon being pushed and bouncy on state change based on speed, the whole main bubble bounces and teleports to random posititions. Resolve, only the icon of the mod animates. Previous behaviour was that it bumps itself towards the direction it has pulled to, intensity and force based on the speed on release  

[] Check if the project uses a fluid merging system across all bubbles and note it in .claude to never forget to implement and take care of this feature. EVERY SINGLE BUBBLE IS SUPPOSED TO BE A FLUID and thus 

[] add debugs for every possible state, displaying an example animation of that state (it will set up the needed environment first for the state needed)
Like for example getting a notification, swiping a satellite bubble and it becomes a the main bubble, closing a satellite, closing a mod and entering idle, etc. badically every possibility 
Continue to add debugs as new states are added. Do not tick this task

[] discord names are centered, altough they should be on the right, text expands from right to left, until a point to not overlap with punchole
Make sure for everything that it never touches the punch hole and has a set margin between it for idle states


[] make sure everything is in english. The content itself is German because phone is German, but everything i can control should be english

[] Haptic is extremely fragile to issues and animation failures. Sometimes Haptic doesn't animate, other times it animates very lagging and stutters

[] Haptic Scale animation is supposed to be an additive animation. If the bubble is being dragged a little and I actually intended to use Haptic, the Bubble should animate dynamically based on on the changed values and cleanly add the scale to it

[] overflow should be set to visible for everything that is a bubble itself
There is a big issue with this that some bouncy bubble animations are cut off by invisible borders and thus look a little scuffy, make sure that nothing is ever cut off

[] when getting a notification and within the bubble active animation a new notification of the same person/app happens, (it should expand the bubble downwards if content doesn't fit) it should append the new entry and slide it in from the bottom with the border text fade underneath the last notification within that time window, this continues and resets each time until the bubble has closed again. If bubble size has to be increased it should be animated and not instantly expanded 

[] System notifications still overlap with the bubble. The behaviour of bubble overlapping system notifications is still not being met
Do not suppress them but instead make sure the bubble is ALWQYS rendered on the lowest index system wide


[] If an alarm is ringing then the bubble should increase its size to full screen with margin like the one on top and become orange.
Add the usual buttons at the very bottom as circles with a squircle border radius.

[] Charging should be a special state, this basically is a true main secondary bubble, meaning it behaves totally alone by itself and differently. from every other state, its also not a Now state but a "Double" state
Its behaviour is supposed to be an extension of the main bubble, when its basically active, open or any other state. Its a hard task to pull off but this bubble should have a magnetic liquid interaction on the edges of the main bubble when overlapping e.g. get a notification (alert) exactly at the moment or during the charge animation vice versa (this is actually the behaviour between main and satellite bubbles)


[] discord should support the video display if someone is streaming or has video active in a call. If this is the case open tap enlarges the bubble to fit width on screen 16:9, or if verticall layout its usual aspect ratio. Haptic hold in that case opens discord here, clicking active discord video mod will then open discord


[] on the lockscreen add a new bubble that represents the lock icon. It should be positioned where the lock icon is on the lock screen and act as an indicator when unlocking.
On unlock the lock opens in an animation and then the bubble moves into the main bubble, making the main bubble have a little bouncy wave ripple animation. It should have its fluidity like the main and secondary bubble (every bubble should behave like this )


[] now state should have the same fluidity interaction between the bubbles just like main and secondary bubbles have (the merging behaviour if they are close by each other), this merging behaviour should become a common behaviour in this project and saved to rules and styleguide etc

[] now state should cover the width position and sizes just like when spotify is active (as a reference)

[] recording should be the same functionality and state as the flashlight (The Now State. displayed on the left next to the clock) with the whole pill as red and the icons and content white. animate the icon when it changes from play to pause vice versa also display the current recorded time
tapping this bubble will pause the recording if its running. tapping again will resume. Haptic press on this bubble will open the bubble and display the time, pause/play and stop recording as options 

[] Add a dithering hue from the bottom to somewhat 35% of the bottom to Spotify in the Spotify hue
Add a Dot to the current end of the song tirmeline
Add squircle borders around the buttons

[] Add a new Mod named Download, it will track the download of something in real-time, Animated download icon on the left, a little timeline on the right. Active tap expands it and shows the information better, with the app icon where the download happens, Haptic will open the app where the download is from. On complete the Bubble changes and replaces download
Do the same for Upload and name this mod Upload instead, basically the same just for upload 

[] Add bluetooth connection to Now Bubble. It will identify the device connected and display its icon on the left side. If it has a charge it will display it on the right side in %. Active will expand it and show settings for this device.

[] Add usb connection (debug etc) to Now Bubble

[] Add a new Bubble named Status, at the right end of the status bar. It replaces the system icons standing there: battery, wifi / mobile connection, and the Netzwerk balken, and it also carries Modus.
While a Modus is active the whole Status Bubble takes that Modus' colour and its icon is appended on the left side of the bubble.
Same rules as every other bubble: born at the punch hole, liquid with whatever it comes near, drawn on the canvas in pill.html, never its own window.

[] Satellite-Spotify has a different icon image border scale than when its Main-Idle-Mod-Spotify, looks janky when changing bubbles. The icon is supposed to be always fully rounded

[] Satelllite-Clock rerenders its clock when switching from satellite <-> main. It should continue its animation from where it was left (the tick)


[] Now Bubble in status has different mods, Torch, Recording/screen sharing, Download and Upload should be two of them
Download will play a Download animation and check a done tick animation, the merging with main bubble, same for upload but with upload, add the x/xmb download and download speed spirited by a.
Each mod has a different width > chang the width of torch to not big as big as it is right now. Also add a toggle in settings to either push the left satellite to the right/hidden or not push it when Now Bubble is being activated 

[] Add a slider in settings to edit the strength of the Goo: higher value higher distance

[] Locked Now Bar should get a shadow drop with its satellites at the bottom, subtle but noticeable 

[] Blurs on locked now bar sometimes do not render when activating phone 

[] Bigger dynamic and context change
Lock screen bubbles should directly interact and animate based on how much i have swiped already on the lock screen. Kinda like the os clock disappearing but instead the bubbles slightly drag in a rubber band style; unlock continues the animation like we did with lock now bubble

[]if a mod is abrublty closed via closing app there is no animation for the bubble
system should see this amd appropriately animate it

[] alerts often times bug themselves and have a large accent colored hue when closing. in very rare cases the entire alert stops mid animation and stays the size it has stopped, especially prone to this issue when phones locked

[] Media like audios from telegram or WhatsApp, should be able to be sped up, these apps have internal toggles for this

[] DB Navigator gets a mod State. Idle will display the Zugnummer/identification on the left, with the remaining time in hh:mm format on the right, this is for when traveling currently witz the train. Umstieg at train station will display other stuff, with the Gleis "G-12" on the left for example and the remaining time until take off on the right side.
I hope the DB API is exposed on the phone or smth like that if I have saved a travel
Tap on idle expands the view and shows information for full travel.
Tap on expanded will open the app
Haptic on idle will open the app and redirect me instantly into the "Abo" tab with my ticket

[] media player while expanded, when dragging the timeline it should ONLY focus the timeline. Now it uses haptic on the bubble, prevent actions beside timeline pulling the bubble when pulling timeline 

[] settings; let me individually change the width of when a mod is running, idle, now, and status bubbles 

[] Add TimeTree with color green/white to types (like telegram, insta, etc) these are named types

[] Bubbles have a dragradius. I would like you to increase it by 100% but add a thresholdconstrainer at the current 80%. If this dragthreshold has been reached during a drag; haptic automatically resets to idle and does not fire off

[] if phone has been unlocked and opened without activating lockscreen first, the bubble should already be on status height and not animate from lock now 

[] if an image is attached to a message, nothing about the image is visible.
Also image related; telegram image doesn't render 

[] closing an expanded bubble back to idle renders the whole bubble without alpha and blur during the close animation. Fix that alpha and blur is always rendered

[] Add specific quick settings on notifications like this example.
Red marks the position where it should be 
Images provided:
![[Screenshot_20260827_092952_One UI Home.jpg]]![[Screenshot_20260827_092950_One UI Home 2.jpg]]

[] mobile Hotspot should be a now state 

[] notifications should display the time passed from that notification in minutes.

[] Issues with the blur; if a Bubble overlaps another bubble the blur does not contain the content of the overlapped bubble. Seems like blur is drawn behind the content, should draw above it so the bubble content is also blured

Before you do anything, list to me every single Bubble possibility, eg Now, Main, Satellite, Double, Now Locked, Status.
Mods are content injections into a bubble, every Bubble can have any mod, yet they are strictly split, like the torch mod should only be used by the Now Bubble, while Spotify is eligible for Main, Satellite and Locked Now Bubble.

Status behaves a little differently than other Bubbles. It has no real mods but displays instead the battery and other status icons.
Also a Clock Bubble will exist, replacing the clock on the top left of the status bar 

Important rule; Every bubble has the same properties: Bouncy, gooey, animated, smooth and state driven based on mods (exception are Clock and Status, they do not own mods) and are also smaller by a few pixels in height.
The Clock Bubble will have a visible merging process with the Now Bubble, if Now Bubble is running, never overlapping each other; important because we are going to move the 3 most recent notifications into the Now Bubble.
Swiping the now Bubble Up should dismiss the Now Bubble (bouncy animation to top from there to center punchhole, dynamic as we talked about adapting the Now Locked Bar behaviour and improving it across the entire system)
The Status Bubble will display connectivity stuff, like Bluetooth connected to a device, usb connection, Hotspot active, wifi connected, and battery always on the most right.

This project is coming closer and closer to actually replacing the Status Bar of a Samsung, and there are some settings that need to be pointed out. The next images tell exactly the settings that have to be set, make it a one button press in the app to set/reset everything. An option to entirely disable status bar would be perfect here.

![[Screenshot_20260827_112735_Settings.jpg]]![[Screenshot_20260827_112750_Settings.jpg]]![[Screenshot_20260827_112818_Settings.jpg]]![[Screenshot_20260827_112936_Settings.jpg]]

Furthermore we will add real Notification Bubbles on the Lock screen (exclusively to there), managing every notification, by adding a scrollable overflow visible container (only scrolling when content is rendered on that position). 


Every Bubble has a dragradius, but Notifications Bubbles do not have one for now until further testing. Tap enlarged the norification group of that person/app, haptic opens the app 




---

A Code refactor is probably going to be necessary, everything is in the main pill file and it covers already too much. Split it into modules and keep it modular, for better performance and reliability.

IMPORTANT. After every feature/fix you are going to push the changes to my github repo, writing exactly what the feature is 

New standard settings from top to bottom:
85
30
0
3
0
0
0
80
60
Both
---

## Added 2026-08-28, after the original note

[] Every google related thing should have google colors except Gmail

[] Time stuff and Dates should be highlighted like a Marker highlight in light blue

[] Money stuff should be highlighted in yellow like with a marker (7,80€ example)
