# Testing the EcoFlow REST API with the Wave 3

This project demonstrates the use of a bit of the EcoFlow REST API to talk to a Wave 3
air conditioner unit. As this page will document, as of 6 June 2026, the API does not
appear to work fully.

## Running the Project

If you want to check out, build, and run this project, you will need an Android 10 or
higher device with Internet access. You will also need to create a `vendor.properties`
file in the project root directory, containing three entries:

- `ECOFLOW_ACCESS_KEY`: the access key for the EcoFlow API that you got from EcoFlow
- `ECOFLOW_SECRET_KEY`: the secret key for the EcoFlow API that you got from EcoFlow
- `ECOFLOW_ONE_SERIAL`: the serial number of a Wave 3 unit

The EcoFlow-specific code resides in [`EcoFlowWave3Manager.kt`](./tree/main/app/src/main/java/com/commonsware/wave3api/EcoFlowWave3Manager.kt), while
`MainActivity.kt` contains a small Jetpack Compose-based UI.

## The Tests

There are four distinct bits of code that exercise the EcoFlow REST API.

NOTE #1: the HTTP requests and responses have the API keys and serial numbers redacted.

NOTE #2: the HTTP requests and responses are copied from the logging output of OkHttp,
the HTTP client library used by this project.

### Validating the Signing Algorithm

The [EcoFlow API general documentation](https://developer.ecoflow.com/us/document/generalInfo),
in "Step8", shows how to validate that the signing of API calls is performed properly.
The `testCall()` function on `EcoFlowWave3Manager` returns the `sign` HTTP header for
a test API call that uses the values specified in "Step8". It correctly returns the
value cited in the documentation, so the signing process seems to work fine.

### Listing Devices

The `/iot-open/sign/device/list` endpoint returns a list of EcoFlow devices registered
to the EcoFlow account tied to the API keys that you have in `vendor.properties`. The
`listDevices()` function on `EcoFlowWave3Manager` hits that endpoint and returns the
list of devices.

The resulting HTTP request looks like:

```
GET https://api-a.ecoflow.com/iot-open/sign/device/list
accessKey: **ECOFLOW_ACCESS_KEY**
nonce: 111394
timestamp: 1749238517027
sign: 86dd2d6f58136f7b7a6e7b6b8fb6576f332315835b512fb69cb6a7784b2f69be
```

The result shows that the call worked as expected:

```
200 https://api-a.ecoflow.com/iot-open/sign/device/list (494ms)
content-type: application/json;charset=UTF-8
vary: Origin
vary: Access-Control-Request-Method
vary: Access-Control-Request-Headers
x-timestamp: 1749238517082
server: TencentEdgeOne
content-length: 167
strict-transport-security: max-age=16070400;
date: Fri, 06 Jun 2025 19:35:17 GMT
eo-log-uuid: 16111073280753415343
eo-cache-status: MISS
{"code":"0","message":"Success","data":[{"sn":"**ECOFLOW_ONE_SERIAL**","deviceName":"WAVE 3-2282","online":1}],"eagleEyeTraceId":"79a56eec112253e2a0a1d28c83e6adf2","tid":""}
```

So, this call seems fine. However, note that it is not specific to the Wave 3 &mdash; this
call is for devices across the EcoFlow ecosystem.

### Getting Full State

The `/iot-open/sign/device/quota/all` endpoint is documented to be "Query device's all quota infomation" [sic].
The sample response shows that it should return a `data` JSON field containing a bunch of
name/value pairs:

```json
{
    "code":"0",
    "message":"Success",
    "data":{
        "bmsMaster.soc":"100",
        "bmsMaster.temp":"34",
        "bmsMaster.inputWatts":"0",
        "bmsMaster.outputWatts":"0",
        "pd.remainTime":"14781",
        "inv.cfgAcEnabled":"0",
        "mppt.carState":"0",
        "pd.usb1Watts":"0",
        "pd.usb2Watts":"0",
        "pd.qcUsb1Watts":"0",
        ...
    }
}
```

The `getFullState()` function on `EcoFlowWave3Manager` hits that endpoint and tries to
return that map of key/value pairs. **This does not work properly.**

The request seems fine:

```
GET https://api-a.ecoflow.com/iot-open/sign/device/quota/all?sn=**ECOFLOW_ONE_SERIAL**
accessKey: **ECOFLOW_ACCESS_KEY**
nonce: 228763
timestamp: 1749238517563
sign: 1106aa43d2d36f90774f436271943bf5600d26ec12f8c5781633df4d9a02162d
```

However, the response does not match the documentation:

```
200 https://api-a.ecoflow.com/iot-open/sign/device/quota/all?sn=**ECOFLOW_ONE_SERIAL** (155ms)
content-type: application/json;charset=UTF-8
vary: Origin
vary: Access-Control-Request-Method
vary: Access-Control-Request-Headers
x-timestamp: 1749238517269
server: TencentEdgeOne
content-length: 94
strict-transport-security: max-age=16070400;
date: Fri, 06 Jun 2025 19:35:17 GMT
eo-log-uuid: 10032559131394765850
eo-cache-status: MISS
{"code":"0","message":"Success","eagleEyeTraceId":"dc5b7ca0a95784145bbc41099a6b5801","tid":""}
```

Notably, the JSON **does not contain `data`**.

I get the same results when using `curl`, so I am reasonably confident that the issue
does not lie in the Kotlin code in this project.

### Powering On the Unit

An HTTP `PUT` request of some JSON to `/iot-open/sign/device/quota` lets you send commands to an
EcoFlow device. [The documentation for Wave air conditioners](https://developer.ecoflow.com/us/document/waveAir)
shows that one of those commands is for "Remote startup/shutdown (1: Startup; 2: Standby; 3: Shutdown)".

The sample command JSON shows the structure:

```json
{
    "id": 123456789,
    "version": "1.0",
    "sn": "KT21ZCH2ZF170012",
    "moduleType": 1,
    "operateType": "powerMode",
    "params": {
        "powerMode": 2
    }
}
```

The `changePowerState()` function on `EcoFlowWave3Manager` hits that endpoint and tries to
change the power mode, and the sample app specifically tries to turn on a Wave3. **This too does not work.**

The request seems like it should match what the API calls for:

```
PUT https://api-a.ecoflow.com/iot-open/sign/device/quota
Content-Type: application/json;charset=UTF-8
Content-Length: 126
accessKey: **ECOFLOW_ACCESS_KEY**
nonce: 850576
timestamp: 1749238517740
sign: 04c3869d01e694809f555b7a007a2dd7f8b6b895c7aefa6f0fc1f2c28a10a8b9
{"id":1749238517735,"version":"1.0","sn":"**ECOFLOW_ONE_SERIAL**","moduleType":1,"operateType":"powerMode","params":{"powerMode":1}}
```

However, instead of a `"code":"0","message":"Success"` sort of response, we get an
error:

```
200 https://api-a.ecoflow.com/iot-open/sign/device/quota (152ms)
content-type: application/json;charset=UTF-8
vary: Origin
vary: Access-Control-Request-Method
vary: Access-Control-Request-Headers
x-timestamp: 1749238517433
server: TencentEdgeOne
content-length: 121
strict-transport-security: max-age=16070400;
date: Fri, 06 Jun 2025 19:35:17 GMT
eo-log-uuid: 16347219110888806393
eo-cache-status: MISS
{"code":"1000","message":"analyse protobuf message failed","eagleEyeTraceId":"626c6e3e794c3357b3a7b5e5ed6b4030","tid":""}
```

Notably, we get an error message of `analyse protobuf message failed`, and
**it is unclear what this message means**.
