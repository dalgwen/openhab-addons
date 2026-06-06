# Signal Binding

This binding connects to the Signal network and allows openHAB to send and receive messages through it.
It relies on the [signal-cli](https://github.com/AsamK/signal-cli) project.
By default, it will manage the download and installation of signal-cli automatically (a custom graalVM native build).
It is also possible to use a pre-installed signal-cli binary (local or remote).

This binding also relies on a binary library compiled for specific architecture. These architectures are theoretically supported:

* amd64 for Windows
* amd64 for Linux
* arm64 for Linux
* aarch64 for OSX

These architectures **WAS** supported, but **NOT ANYMORE** (openHAB support dropped, or I didn't find a way to build them on Github runners):
* amd64 for Linux (musl linked lib for distrib like alpine) - use the env variable `org.osgi.framework.os.libc=musl` to select this version
* amd64 for OSX
* arm for Linux

## Supported Things

Things supported by this binding :

- A *signalaccount*, representing a fully fledged account, with a number dedicated to it.
- A *signallinkedaccount*, representing a "linked" account, when you want to share your personal number with it.
- A *signalconversation*, representing a conversation with another user.

## Discovery

There is no discovery process for *signalaccount* or *signallinkedaccount* thing.
A *signalconversation* thing will be discovered and added to the inbox everytime the bridge receives a message by a new sender.

## Storage and security

Be aware that this Signal binding stores secret keys, id, etc, on your disk (unencrypted). Take appropriate measures protecting the directory <OPENHAB_USER_DIR>/signal.

Note that if you use docker with openHAB, you may have to lift the cryptography restrictions for signal-cli. See [this link](https://github.com/openhab/openhab-docker#java-cryptographic-strength-policy) for more details.

## Configuration

The binding has two configuration parameters.

- The main one is the "kind" of signal-cli integration you want :
  - MANAGED: the binding will download, install, and run signal-cli automatically.
  - LOCAL: the binding will use the signal-cli binary installed on the system. You have to provide the path to the binary in the configuration. The binding will launch it.
  - NETWORK: the binding will connect to a remote signal-cli instance. You have to provide the URL of the remote signal-cli instance in the configuration. You have to launch it yourself.
- The second is an additional configuration string.
  - for MANAGED, it is the version of signal-cli you want to install. Let it empty to have the default value. Available versions are [here](https://openhab.jfrog.io/ui/repos/tree/General/libs-runtime-deps/AsamK/signal-cli). You may have to update to a more recent value if Signal has changed its protocol.
  - for LOCAL, it is the path to the signal-cli binary. You can provide at the beginning of the 'configuration' parameter, environment variables to customize the process execution.
  - for NETWORK, it is the ip:port of the remote signal-cli instance.

## Captcha (for signalaccount only)

Unfortunately, registering a custom agent (a non-mobile device) requires an additional step. Signal protects its network with a captcha anti-bot registration system.

You must provide a string, returned by a dedicated web page, proving that you are a human. You have then to provide it to the binding.

To get the captcha, you have to pass a verification check on the URL https://signalcaptchas.org/registration/generate.html and copy/paste the resulting link during the registration process.


## Thing Configuration

A bridge thing, either a *signalaccount* or a *signallinkedaccount*, is required.

### Dedicated Signal Account

If you have a dedicated number available and not already linked to a device, this should be your first choice.
Tip: you can use a landline number. The only requirement is that you can receive SMS or voice call from the Signal service.

Because Signal requires several verifications, the signal account thing creation is a several-step process.
Once you created the Thing, you have to:

* Get a valid captcha proving that you are human. You can try without one, but Signal will probably reject your attempt (see above).
* Use the 'register' action on the Thing (you can use Main UI or anything else). Fill in the captcha as a parameter. You will then receive a verification code (either by voice or SMS). Note: voice verification can take longer due to signal restriction (+1 minute)
* Use the 'verify' action on the Thing. Set the verificationCode parameter.

For a *signalaccount*, the following parameters are available:

| Parameter Name | type | required   | description |
|----------------|------|------------|-------------|
|phoneNumber| text | yes | A dedicated phone number |
|reactionEnabled| boolean | no | If true, also receive reaction emoji |
|deviceName| text | no | A friendly name for display |

### Linked Signal Account

You can use this bridge thing if you have no spare phone number. You will use your mobile phone application.
This is also a several-step registration process.

* At first, create the thing. It will be in the CONFIGURATION_PENDING state.
* Use the 'link' action on the Thing.  (you can use Main UI or anything else). 
* Then, open the thing with the main UI (you may have to refresh the page) and scan the QR code with your Signal application on your phone (linked devices menu). You have 60 seconds to do so.

| Parameter Name | type | required | description    |
|----------------|------|----------|----------------|
|deviceName| text | no | A friendly name for display|
|phoneNumber| text | yes | The shared phone number |
|reactionEnabled| boolean | no | If true, also receive reaction emoji |

### Conversation

The *signalconversation* thing is just an optional shortcut to exchange messages with a specific number, with convenient channels.

| Parameter Name | type | description |
|-----------|----------|----------|
| recipient | text | The phone number.|


## Channels

### Trigger channels

The *signalaccount* and *signallinkedaccount* has the following trigger channel :

| Channel ID          | event                      |  
|---------------------|----------------------------|
|receivetrigger| The number and the message received (concatenated with the '\|' character as a separator)|

The *signalconversation* supports the following channels :

| channel  | type   | description                  |
|----------|--------|------------------------------|
| receive | String| The last message received |
| send | String| A message to send |
|deliverystatus| String| Delivery status (either SENT, DELIVERED, READ, FAILED)|


## Rule action

This binding provides actions to register (main or linked account). See above for details.

This binding includes two rule actions to send messages.

```
(Rule DSL)
val signalAction = getActions("signal", "signal:signallinkedbridge:<uid>")
```

```
(javascript JSR)
var signalAction = actions.get("signal", "signal:signalaccountbridge:<uid>");
```

Where uid is the Bridge UID of the *signalaccount* or *signallinkedaccount* thing.

Once this action instance is retrieved, you can invoke the 'sendSignal' method on it:

```
signalAction.sendSignal("+33123456789", "Hello world!")
```

You can also send images with this action, using file path, web resource, item of type Image, or direct dataUri :

```
signalAction.sendSignalImage("+33123456789", "/path/to/image.png", "Look at this !")
signalAction.sendSignalImage("+33123456789", "https://www.openhab.org/openhab-logo-top.png", "")
signalAction.sendSignalImage("+33123456789", MyImageItem.state.toFullString, "")
signalAction.sendSignalImage("+33123456789", "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABQAAAAUCAIAAAAC64paAAAABGdBTUEAALGPC/xhBQAAAAlwSFlzAAAOwQAADsEBuJFr7QAAABl0RVh0U29mdHdhcmUAcGFpbnQubmV0IDQuMC4yMfEgaZUAAAAfSURBVDhPY/j+v49sNKqZRDSqmUQ0qplENPI0/+8DAOnW7m6FxOUUAAAAAElFTkSuQmCC", "Image inline !")

```

When using the linked bridge thing, you can use the special recipient "self" to send a note to yourself. When sending a note to yourself, there is no notification on your other devices.

If you want notification when sending a message to your own account, use the send action with your full number.

#### Return value

Each send action returns a Map<String, Object>. The only member of the map is keyed by the string "RESULT". The value is "OK" or "KO".

## Full Example

### Thing configuration

things/signal.things:

```
Bridge signal:signalaccountbridge:mymainsignalaccount "Signal account for my number" [ phoneNumber="+336123546879"] {
    Thing signalconversation aconversationname [ recipient="+33987654321" ]
}
```

### Send message

`signal.rules` for DSL:

```java
rule "Alarm by SIGNAL"
when
   Item Alarm changed
then
   val signalAction = getActions("signal","signal:signalaccountbridge:mymainsignalaccount")
   signalAction.sendSignal("+33123456789", "Alert !")
end
```