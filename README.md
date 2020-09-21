# Set-up instruction for SOFA and TIPS Android controller

* Install SOFA from [SOFA_installer_Aug2020](https://drive.google.com/file/d/1jeCPuuc761pwe0Nb8wc0wNlOAy03TAo5/view?usp=sharing) (right click and 'run as admin' to install)

    * Troubleshooting 1: Can not run the installer due to security reastons.
        
        * try turning off or lowering the security level of your anti-mareware softwares like McAfee or Windows Defender  
    
    * Troubleshooting 2: After installation, if you don't see a file named "myIpAddress.txt" in your SOFA directory. Try:

        * disable the windows firewall and reinstall SOFA

        * manually open "cmd.exe" and run the following command to get the specified IpAddress, 

            ipconfig | findstr /R /C:"IPv4 Address"

        * If you get more than one ip address, ignore the ones start with "192.xxx..."

    * Troubleshooting 3: Scene file crashes upon simulation starts /the scene is not rendered properly, e.g “fatty tissue in the scene is not rendered”. 

        * Solution 1: Upon clicking runSOFA, if the OpenGL version displayed in the log is not "QtViewer: OpenGL 4.5.0 NVIDIA" or higher,you need to 
        set your Nvidia graphic card for SOFA. In the Nvidia Control panel program settings, make sure sofa is using the high-perfomance Nvidia graphic card. 

        * Solution 2: You also need to update the driver of your graphics card to the latest version. 
        Open Device Manager -> Click Display adapaters -> Right Click on your graphics card and Update Driver Software

* Install Python 2.7 
    * [Python download](http://www.python.org/downloads/release/python-2718/)

* Install Haptic Device Drivers and SDK from (reboot may be required after installation): 
    
    * [openHaptics] (https://drive.google.com/file/d/1geOE_uqyYM-YhK-RGJ1CYc-jjD-zmTN7/view?usp=sharing)
        
    * [haptic drivers] (https://drive.google.com/file/d/1kQlm0aenXD2Lp-IOLL8qQ5KQW0fo3Z6u/view?usp=sharing)

* Install TIPS Android controller to your cellphone and follow the instruction in the APP: 
    
    * [APP download](https://bitbucket.org/surflab/tips-android-controller/downloads/app-release.apk)

* Make sure your phone and PC are connected to the same network(WIFI) before running the simulation.

* Prepare the scenes:
    * Download the scene file below, open it using any text editor and search for the keyoword "inServerIPAddr", then 
    replace the ip address after this keyword by your ip address as in the above step. There are two in the scene file to be replaced.
    
    * Lap Appendectomy: https://bitbucket.org/surflab/tips-android-controller/downloads/Appendect_Sep15.scn
    * Lap Chole: https://bitbucket.org/surflab/tips-android-controller/downloads/LapChole_android.scn
    * Lap Adrenalectomy: https://bitbucket.org/surflab/tips-android-controller/downloads/Adrenalectomy-android.scn
    * Lap Hepatectomy: https://bitbucket.org/surflab/tips-android-controller/downloads/Hepatectomy-android.scn

**Issues / Some useful notes for programmers**
------------------------------------------------

## SOFA SERVER SIDE (C++):

    1. c++ UDP communication examples:
    	https://causeyourestuck.io/2016/01/17/socket-cc-windows/
    	better e.g. https://www.winsocketdotnetworkprogramming.com/winsock2programming/winsock2advancedcode1e.html
    	TCP vs UDP: 
    		UDP is faster, simpler and more efficient than TCP. 
    		TCP is reliable as it maintains connection first, guarantees delivery of data to the destination router.

    2. Issue (Linking) : Undefined Reference to WSAStartup...
    	linking issue : https://stackoverflow.com/questions/34384803/c-undefined-reference-to-wsastartup8
    	in visual studio, just add "ws2_32.lib" to the linker

    3. Issue : Redefinition Header Files (winsock2.h)
    	The problem is when windows.h is included before WinSock2.h as windows.h includes winsock.h. The simple solution is to 
        use winsock.h instead.see https://stackoverflow.com/questions/1372480/c-redefinition-header-files-winsock2-h/3253327

    4. For UDP server, we must use "SOCK_DGRAM" and "IPPROTO_UDP" in the following socket function, default will be TCP;
    	server = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP); 

    5. Error c2678 :binary '==' no operator found which takes a left-hand operand of type
    	solution: Stop using "using namespace std" statements once and for all, this "bind()" conflicts with the STL std::bind function.

    6. Issue : c++ main thread been blocked by the server
    		solution:  do not use join(), as join() will block the calling thread until the thread represented by the boost::thread object has completed.
       Similar issue: how to handle java thread?
       		solution : https://www.youtube.com/watch?reload=9&v=QfQE1ayCzf8

    7. How to hanle two devices:
        server thread is in static function (to have only one server for two devices)
        update the non-static members in SOFA animation loop (onAnimationBeginEvent) and sync with the sever static vars


## Android CLIENT SIDE(java):

    1. Android Earth Coords: East-North-Up coordinates.
        Android rotation vector sensor return value[5] : (qx, qy, qz, qw, accuracy)
        // values[0]: x*sin(&#952/2)
        // values[1]: y*sin(&#952/2)
        // values[2]: z*sin(&#952/2)
        // values[3]: cos(&#952/2)
        // values[4]: estimated heading Accuracy (in radians) (-1 if unavailable)

    2.Features-list
        (done)figure out Quaternion!!!!!
            vecToolTipDirectionInSOFA = androidQuat * (correctionQuat?) * initialCameraViewQuat * defaultToolTipDirectionVector;
            figure out initialCameraViewQuat...
        (done) figure out how to switch between udp and HD (for now HD does not respond when udp is active)
        (done) two-way communicatio: vibration on the phone when collide in SOFA
        (done) set ActionButton to be the Hardware VolumeControl Button
        (done) prevent multi-touch
        (done) independent aspect ratio
        (done) gesture based start detection
            obtain and transfer the ip info in an more automatic way.

    3. win10 IP/Port instruction:
        Run the windows "cmd" as administrator
        To find ipv4 address: ipconfig | findstr /R /C:"IPv4 Address"
        To open a port: netsh advfirewall firewall add rule name="TIPS Port 5556" dir=in action=allow protocol=UDP localport=5556