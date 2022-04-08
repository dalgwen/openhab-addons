
# Multi / Long Press Transformation Service

Transform button presses on a rocker to a detailed String.
Handle multi press, or long press.

| Action | Resulting Command |
|--------|------------------|
|"button1" press and immediately release|"button1.1" |
|"button1" press and release three times in rapid succession|"button1.3" |
|"button2" press and release four times in rapid succession|"button2.4" |
| Press "button1" during two seconds, then release|"button1.0"<br>"button1.0"<br>"button1.0"<br>"button1.0"<br>(one command with "0" suffix, every 500 milliseconds)|


## Usage as a Profile

The transformation must be used as a `Profile` on an RawRocker link.

## Paramaters

| Name | Description |
|--------|----------|
|button1  | Button1 name returned in the Command String (default: "1")|
|button2  | Button2 name returned in the Command String (default : "2")|
|maxRepetition|This is the maximum number of repetition to do for a long press. Usefull as a safeguard when the 'release' event is not detected. -1 for no limit (default)
