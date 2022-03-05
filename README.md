[comment]: <> (This is a comment, it will not be included)
#  TIPSlite  prerequisites

:iphone: Ph = Android or iphone 

:computer: PC = thin client (laptop) to display remote simulation 

* cell phone pen (50 cents) [affixed upright][]
 pen tip serves as laparoscopic fulcrum point (pivot, trocar) for your strong hand
recommend height: your arm forms a right angle when the cell phone touches the top of the pen.
[affixed upright]: https://www.cise.ufl.edu/research/SurfLab/TIPS/TIPSlite.php 
* If right-handed install slightly to right of monitor.
* PC mouse [to pull tissue with your weak hand, you may want to slow mouse speed in settings]
* good internet connection  (run speedtest: download speed > 50 , upload > 20 works)
* bluetooth on PC and Ph (need to be able to pair)

#  TIPSlite bluetooth usage
Follow steps in order. Do not start any app until after :gear: installing PC app, Ph app, and pairing! 

* :iphone: Ph   
    *  Android: install [TIPSlite app][] (bluetooth version) 
    *  iphone: install testflight app from Apple, request testflight access, refresh to newest TIPSlite app
[TIPSlite app]: https://drive.google.com/file/d/161IanZu-_D5fK9aboecDszYJ0FA6zNhJ/view?usp=sharing

* :computer: PC install [TIPSlite_Client][] 
[TIPSlite_Client]: https://drive.google.com/file/d/1pj7iG7y8XZ_pcASdEvUI8Cpc_AjyRjg3/view?usp=sharing

    * Unzip binaries (zip or 7zip on right click will work)

* :computer: PC Enable Bluetooth

* :iphone: Ph Enable Bluetooth

* :gear: [Pair Ph and PC via Bluetooth][]. Verify pairing in (BT & other devices)  see Fig 0 below
[Pair Ph and PC via Bluetooth]: https://support.microsoft.com/en-us/windows/pair-a-bluetooth-device-in-windows-2be7b51f-6ae9-b757-a3b9-95ee40c3e242

[comment]: <> (old: &nbsp; Note:  Android app must start after PC app,  iphone app before)

* :computer: PC Open: ``` TIPSLite_App_Bluetooth\TIPSClient_Start.bat ```

* :iphone: Ph open App (iphone: TIPSControllerIphone, Android:  TIPS_Controller)  
    * Android: Choose PC in the dropdown list, hit JOIN 
    * :computer: Within 10 seconds, the Worker, Coordinator, STUN and BT status should turn green. see Fig 1 (vpn may need to be switched off!)
&nbsp;

* :computer: PC in list of available servers, select the TIPS_LapChole or TIPS_LapAppendect procedure

* :computer: PC after selection click Connect (remote simulator app opens in Remote Viewer window in 5 seconds)

* :computer: PC Enter name and email  -- or hit ok to stay anonymous

* :computer: PC Maximize the simulator window

* :computer: PC Spacebar starts the simulation   
  (You can't interact with the scene if you don't!)

* :iphone: Ph Calibrate = point Ph top at the screen (See Fig 2)
  (use phone as strong hand surgical tool). You should see Fig 3 or Fig 4 once calibrated.

&nbsp;

Enjoy the simulation

* :computer: PC Look at upper left for instructions:  
    * Ctrl+c changes Ph tool 
    * C: toggle mouse-as-camera  
    * PC mouse for small weak hand tool: clamp and pull tissue

* :iphone: volume button = cauterize, clamp, cut, deploy etc. (iphone needs to use vol up and vol down)
 

*  Disconnect,  :heavy_multiplication_x: the Remote Viewer window.


# Trouble Shooting
* :computer:  PC client status "STUN", "Coordinator" are not turning green?
     * Anti-Virus software: add TIPSlite Client to exception list (or disable Anti-Virus)
     * Windows Firewall/Defender: click "trust and executed anyway" to extract files
     * VPN needs to be turned off
* :computer:  I opened the iphone APP, but the client bluetooth status "BT" are not turning green?
     * For the first time it may take longer (e.g. 30 secs) for iphone to connect to the client
     * Install and run [BLE explorer][], if it can not list your iphone, your bluetooth adapter may not support Bluetooth LE.
[BLE explorer]: https://www.microsoft.com/en-us/p/bluetooth-le-explorer/9n0ztkf1qd98?activetab=pivot:overviewtab
* :iphone: 
     * Flipping not detected: disable "lock in portrait mode" to free gyroscope
     * First attempt works but second attempt fails: completely closes the PC client and the phone app and then start again.
     * Can not find the instrument


![win10_pair_bluetooth.png](https://bitbucket.org/repo/Kd7K76K/images/2385096850-win10_pair_bluetooth.png)
![RAC_ready.png](https://bitbucket.org/repo/Kd7K76K/images/1212226366-RAC_ready.png)
![calibrate.png](https://bitbucket.org/repo/Kd7K76K/images/3789107691-calibrate.png)
![9270e200-7f27-4142-a2c0-fdeea8453330.png](https://bitbucket.org/repo/Kd7K76K/images/4026537807-9270e200-7f27-4142-a2c0-fdeea8453330.png)
![Iphone_ready.png](https://bitbucket.org/repo/Kd7K76K/images/3192818153-Iphone_ready.png){:height="5%" width="5%"}

# FAQ
* Can I take a break?
     * just lay the cell phone down;
     * to continue ensure that the phone is still connected, else JOIN again recalibrate flip and continue.
* The program crashed.
     * there are many causes: someone else logged in and did not read the FAQ, you hit the wrong key on the keyboard, the network connection of the server was reset (or yours).
Simply restart (sorry the state is currently not saved):
exit the client app,
restart the cell phone app the PC client.

* I need both hands to move the camera (mouse) and change the tool
     * just lay the cell phone down; then continue