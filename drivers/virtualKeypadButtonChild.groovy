/**
 *  Virtual Keypad Button Child
 *
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Change History:
 *
 *    Date        Who            What
 *    ----        ---            ----
 * 	 9-26-20	mbarone			initial release
 */
metadata {
	definition (name: "Virtual Keypad Button Child", namespace: "mbarone", author: "mbarone", importUrl: "https://raw.githubusercontent.com/michaelbarone/hubitat/master/drivers/virtualKeypadCommandButtonChild.groovy") {
		capability "PushableButton"
		capability "Momentary"
		capability "Actuator"
		capability "Switch"
	}

    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
	}
	
	command "push"
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def installed() {
	off()
    updated()
}

def updated() {
    if (logEnable) runIn(1800,logsOff)
}

def push(evt) {
	if (logEnable) log.debug "push() called"
	def btn = device.deviceNetworkId.split("-")[-1]
	if(btn == "Clear"){
		sendEvent(name: "pushed", value: "1", isStateChange  : true)
		parent.buttonPress("${btn}")
	} else if(btn == "Number"){
		sendEvent(name: "pushed", value: evt, isStateChange  : true)
		parent.buttonPress("${evt}")		
	} else {
		sendEvent(name: "pushed", value: "1", isStateChange  : true)
		btn = device.deviceNetworkId.split("-")[-2]+"-"+btn
		parent.buttonPress("${btn}")
	}
}

def on() {
	if (logEnable) log.debug "on() called"
	sendEvent(name: "switch", value: "on")
	runIn(5,off)
}

def off() {
	if (logEnable) log.debug "off() called"
	sendEvent(name: "switch", value: "off")
}