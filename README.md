# ATWDPC
Android Things Device Provisioning Client

This is an android example app that connects to an Android Things device running [ATWDP](https://github.com/chmoder/ATWDP) via Bluetooth Low Energy.

It does this by finding the device, then presents the user with a dialog to capture the WiFi SSID name and password.  On positivebutton it sends the credentials to the android things device.  Then the android things device connects to the WiFi.

I would much rather have this concept contained in a module or something, if you have suggestions on how to improve it please let me know!
