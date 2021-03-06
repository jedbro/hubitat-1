/**
 *  Virtual Keypad Switch Child
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
 * 
 */
metadata {
	definition (name: "Virtual Keypad Switch Child", namespace: "mbarone", author: "mbarone", importUrl: "https://raw.githubusercontent.com/michaelbarone/hubitat/master/drivers/virtualKeypadSwitchChild.groovy") {
		capability "Switch"
		capability "Relay Switch"
		capability "Actuator"
		capability "Sensor"
	}

    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
	}
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

def parse(String description) {
    if (logEnable) log.debug "parse(${description}) called"
	def parts = description.split(" ")
    def name  = parts.length>0?parts[0].trim():null
    def value = parts.length>1?parts[1].trim():null
    if (name && value) {
        // Update device
        sendEvent(name: name, value: value)
    }
    else {
    	log.error "Missing either name or value.  Cannot parse!"
    }
}

def on() {
	sendEvent(name: "switch", value: "on")
	runIn(5,off)
}

def off() {
	sendEvent(name: "switch", value: "off")
}