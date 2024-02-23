
# Timed Event Transformation Service

A collection of profile

Transforms button press to a detailed character string. Handle multi press and long press.
Long press is available only for devices that can send another event besides the "activation" event. This second event can be a standard "release" event, or even an "off" event if you want to use a switch for this.

Consult this table to see what this profile can do:

| Action | Resulting Command | Availability |
|--------|------------------|---------------|
|Button 1 pressed and immediately released|"PRESS_A.1" | Always |
|Button 1 pressed and released, three times in rapid succession|"PRESS_A.3" | Always |
|Button 2 pressed and released four times in rapid succession|"PRESS_B.4" | Only for "rocker" thing that has two buttons |
|Button 1 pressed during two seconds, then released|"LONGPRESS_A.1"<br>"LONGPRESS_A.1"<br>"LONGPRESS_A.1"<br>"LONGPRESS_A.1"<br>(sent every 500 milliseconds)| For channel that has a command or state that can be considered as "released"|
|Button 1 pressed three times, last one not released during two seconds, then released|"LONGPRESS_A.3"<br>"LONGPRESS_A.3"<br>"LONGPRESS_A.3"<br>"LONGPRESS_A.3"<br>(sent every 500 milliseconds)| For channel that has a command or state that can be considered as "released"|



## Usage as a Profile

The transformation must be used as a `Profile` on any link sending command or state update.

## Paramaters

| Name | Description | Usage |
|--------|----------|----------|
|maxRepetition|This is the maximum number of repetition to do for a long press. Useful as a safeguard when the 'release' event is sometimes not properly detected. -1 for no limit (default)| For long press|
|pressedEventOrState |The profile will interpret this custom event/state as "Pressed". Use this if the event is not automatically recognized||
|releasedEventOrState |The profile will interpret this custom event/state as "Released". Use this if the event is not automatically recognized|Used only for long press.|
|hasReleaseEvent| Boolean. if your Thing channel has something like a 'release' command and you want to use it for long press detection. If it's only a one event button, you MUST set it to false, or multi press won't work either.||
|delay|Delay between events, in milliseconds. Multi-press : for this profile to see a multi-press event, each new button press should occur within this delay. Long press : delay between two events sent to the item||
