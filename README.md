CsmCompile
==
Emits the Control State Machine (CSM) used by the TBv2 firmware.
                                                                 
The TBv2 uses a state machine, the CSM, to control operation of the 
device. The CSM describes which events, such as key presses, or the end
of audio playback, the device responds to, and what it does to respond.

The compiled CSM is a compact binary format that can be read into memory
and interpreted with minimal processing. This helps speed startup time 
because no parsing is required and the CSM can be assumed to be valid.
(It wouldn't hurt to add a simple length and checksum check.)

Because the CSM files are compact, and because they can be loaded very
quickly, it is practical to load several "scripts" at one time, for
different purposes. The first use of that capability is to implement
script files for custom surveys.

This module contains code to parse a YAML description of a TBv2 firmware
and create the binary file that is used directly by the firmware. See
the file `Compiler.java` for a description of the expected format. The
definitions of the Events and Actions of the TBv2 are imported from the
firmware project. See the file `CsmEnums.h` for instructions on how to
update the file in this project. _Note that the Java version of the Actions
and Events must be re-built manually when the firmware versions change._ 
Note also that the instructions assume a particular layout of the source
repositories on your workstation, and assumes the presence of gcc.

There is also a de-compiler feature that can read a binary CSM and produce 
a YAML listing from it.
               
Programatic Use of CSMcompiler
--

In addition to a command line compiler, a CsmData object can be created
programatically and used to create a binary CSM. 

To use the compiler programatically, import CSMcompiler from GitHub. In `build.gradle`:
```
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/LiteracyBridge/CSMcompile")
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("USERNAME")
            password = project.findProperty("gpr.read_key") ?: System.getenv("TOKEN")
        }
    }
    flatDir {
        dirs '../../CSMcompile/build/libs'
    }
}

if (new File(("../../CSMcompile/build/libs/CSMcompile-2.1.8.jar")).exists()) {
    implementation name: 'CSMcompile', version: '2.1.8'
} else {
    implementation group: 'org.amplio.csm', name: 'csmcompile', version: '2.1.8'
}
```
In your `~/.gradle` directory, create a file `gradle.properties` with a *personal access token* from GitHub. The token must grant read access to the CSMCompiler repository. See [this page](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens) on GitHub for more information..
```
gpr.user=user@example.com
gpr.read_key=${your-read-personal-access-token-from-github}
```
 
Publishing CSMcompiler for Programatic Use
--
Create a *personal access token* with write access to the CSMcompile 
repository. Update your `~/.gradle/gradle.properties` file with that
token:
```
gpr.key=${your-write-personal-access-token-from-github}
```
Then use the gradle target "publish" to publish to GitHub.


