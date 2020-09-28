/**
 *  Virtual Keypad
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

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
 
metadata {
	definition (name: "Virtual Keypad", namespace: "mbarone", author: "mbarone", importUrl: "https://raw.githubusercontent.com/michaelbarone/hubitat/master/drivers/virtualKeypad.groovy") {
		capability "Lock Codes"
	}

    preferences {
		input name: "optEncrypt", type: "bool", title: "Enable lockCode encryption", defaultValue: false, description: ""
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
	}

	attribute "Details","string"

    command "createChildren"
	command "removeChild", [[name:"deviceNetworkID*",type:"STRING"]]
}

def configureSettings(settings){
	state.noCodeRequired = ["none"]
	state.defaultMode = ""
	state.defaultModeTrigger = ["none"]
	settings.each{
		switch(it.key){
			case "armDelay":
				state.armDelay = it.value
				break

			case "armDelaySeconds":
				state.armDelaySeconds = it.value
				break
	
			case "armDelaySecondsGroup":
				state.armDelaySecondsGroup = it.value
				break
				
			case "changeHSM":
				state.changeHSM = it.value
				break
				
			case "changeModes":
				state.changeModes = it.value
				break
				
			case "notify":
				state.notify = it.value
				break
				
			case "notifyLimit":
				state.notifyLimit = it.value
				break			
				
			case "noCodeRequired":
				state.noCodeRequired = it.value
				break
				
			case "defaultMode":
				state.defaultMode = it.value
				break

			case "defaultModeTrigger":
				state.defaultModeTrigger = it.value
				break				
		}
	}
	createChildren()
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def installed() {
	clearCode()
    updated()
    sendEvent(name:"maxCodes",value:20)
    sendEvent(name:"codeLength",value:4)
	state.notifyCount = 0
}

def updated() {
	createChildren()
	clearDetails()
    if (logEnable) {
		log.warn "debug logging enabled..."
		runIn(1800,logsOff)
	}
}

def commandMode(action,btn){
	def displayDevice = getChildDevice("${device.deviceNetworkId}-InputDisplay")
	if(checkInputCode(btn)){
		if(state.defaultMode != "" && state.defaultModeTrigger.any{btn.contains(it)}){
			parent.setMode(state.defaultMode)
		}
		if(state.armDelay && state.armDelaySecondsGroup.any{btn.contains(it)}){
			def timeLeft = state.armDelaySeconds
			state.armDelaySeconds.times{
				timeLeft = timeLeft - 1
				displayDevice?.updateInputDisplay("Setting ${action} in ${timeLeft} seconds")
				pauseExecution(1000)
			}
		}
		if(state.changeModes){
			parent.setMode(action)
		}
		displayDevice?.updateInputDisplay("Success.  Executing ${action}")
		def commandChildDevice = getChildDevice("${device.deviceNetworkId}-${btn}")
		commandChildDevice?.on()
	} else {
		displayDevice?.updateInputDisplay("Input Denied")
	}
	timeoutClearCode()
}

def commandHSM(action,btn){
	def displayDevice = getChildDevice("${device.deviceNetworkId}-InputDisplay")
	if(checkInputCode(btn)){
		if(state.armDelay && state.armDelaySecondsGroup.any{btn.contains(it)}){
			def timeLeft = state.armDelaySeconds
			state.armDelaySeconds.times{
				timeLeft = timeLeft - 1
				displayDevice?.updateInputDisplay("Setting ${action} in ${timeLeft} seconds")
				pauseExecution(1000)
			}
		}
		if(state.changeModes){
			sendLocationEvent(name: "hsmSetArm", value: action, descriptionText: "Keypad Event ${action}")
		}
		displayDevice?.updateInputDisplay("Success.  Executing ${action}")
		def commandChildDevice = getChildDevice("${device.deviceNetworkId}-${btn}")
		commandChildDevice?.on()
	} else {
		displayDevice?.updateInputDisplay("Input Denied")
	}
	timeoutClearCode()
}

def commandCustom(action,btn){
	def displayDevice = getChildDevice("${device.deviceNetworkId}-InputDisplay")
	if(checkInputCode(btn)){
		if(action=="ReArm"){
			displayDevice?.updateInputDisplay("Success.  Executing Disarm")
			getChildDevice("${device.deviceNetworkId}-Custom-Disarm")?.on()
		}
		if(state.armDelay && state.armDelaySecondsGroup.any{btn.contains(it)}){
			def timeLeft = state.armDelaySeconds
			state.armDelaySeconds.times{
				timeLeft = timeLeft -  1
				displayDevice?.updateInputDisplay("Setting ${action} in ${timeLeft} seconds")
				pauseExecution(1000)
			}
		}
		if(action=="ReArm"){
			displayDevice?.updateInputDisplay("Success.  Executing Arm")
			getChildDevice("${device.deviceNetworkId}-Custom-Arm")?.on()
		} else {		
			displayDevice?.updateInputDisplay("Success.  Executing ${action}")
			getChildDevice("${device.deviceNetworkId}-${btn}")?.on()
		}
	} else {
		displayDevice?.updateInputDisplay("Input Denied")
	}
	timeoutClearCode()
}

def timeoutClearCode(){
	unschedule(clearCode)
	runIn(5,clearCode)
}



def checkInputCode(btn){
	if (logEnable) log.debug "checkInputCode"
	
	def codeAccepted = false
	
	if(state.noCodeRequired.any{btn.contains(it)}) {
		codeAccepted = true
		sendEvent(name:"UserInput", value: "Success", descriptionText: "No code was required to execute " + btn, displayed: true)
		if (logEnable) log.debug "${btn} executed with no entered code"
		return codeAccepted
	}
	
	
    Object lockCode = lockCodes.find{ it.value.code.toInteger() == state.code.toInteger() }
    if (lockCode){
        Map data = ["${lockCode.key}":lockCode.value]
        String descriptionText = "${device.displayName} was unlocked by ${lockCode.value.name}"
        if (txtEnable) log.info "${descriptionText}"
        //if (optEncrypt) data = encrypt(JsonOutput.toJson(data))
        //sendEvent(name:"lock",value:"unlocked",descriptionText: descriptionText, type:"physical",data:data, isStateChange: true)
        //sendEvent(name:"lastCodeName", value: lockCode.value.name, descriptionText: descriptionText, isStateChange: true)
		sendEvent(name:"UserInput", value: "Success", descriptionText: descriptionText + " " + btn, displayed: true)
		codeAccepted = true
		if (logEnable) log.debug "${btn} code accepted"
		state.notifyCount = 0		
    } else {
		sendEvent(name:"UserInput", value: "Failed", descriptionText: "Code input Failed for ${btn} ("+state.code+")", displayed: true)
		if (logEnable) log.debug "${btn} code NOT accepted"	
		state.notifyCount = state.notifyCount + 1
		if(state.notify && state.notifyCount.toInteger() >= state.notifyLimit.toInteger()){
			parent.notify("Keypad code input Failed triggered by ${btn} ("+state.code+")")
			state.notifyCount = 0
		}
    }
	return codeAccepted
}

def clearDetails(){
	sendEvent(name:"Details", value:"Running Normally.")
}

def clearCode(){
	if (logEnable) log.debug "clearCode"
	state.code = ""
	state.codeInput = "Enter Code"
	def childDevice = getChildDevice("${device.deviceNetworkId}-InputDisplay")
	childDevice?.updateInputDisplay(state.codeInput)	
}


def buttonPress(btn) {
	unschedule(clearCode)

	if(btn=="Clear"){
		clearCode()
		return
	}

	if(btn.isNumber()){
		state.code = state.code+""+btn
		if(!state.codeInput.contains("*")){
			state.codeInput = "*"
		} else {
			state.codeInput = state.codeInput+"*"
		}
		def childDevice = getChildDevice("${device.deviceNetworkId}-InputDisplay")
		childDevice?.updateInputDisplay(state.codeInput)
		unschedule(clearCode)
		runIn(30,clearCode)
		return
	}

	def type = btn.split("-")[-2]
	def action = btn.split("-")[-1]
	if(type=="Mode"){
		if (logEnable) log.debug "button is mode"
		commandMode(action,btn)
		return
	}

	if(type=="HSM"){
		if (logEnable) log.debug "button is HSM"
		commandHSM(action,btn)
		return
	}
	
	if(type=="Custom"){
		if (logEnable) log.debug "button is Custom"
		commandCustom(action,btn)
		return
	}	
}


def createChildren(){
    if (logEnable) log.debug "Creating Child Devices"
	
	// create buttons
	def theCommands = location.modes.clone()
	theCommands = theCommands.collect { "Mode-$it" }
	//log.debug theCommands
	def HSM = ["armAway", "armHome", "armNight", "disarm", "armRules", "disarmRules", "disarmAll", "armAll", "cancelAlerts"]
	HSM = HSM.collect { "HSM-$it" }
	theCommands.addAll(HSM)
	theCommands.addAll(["Clear","Custom-Arm","Custom-ReArm","Custom-Disarm","Number"])
	//log.debug theCommands

	theCommands.each {
		foundChildDevice = null
		foundChildDevice = getChildDevice("${device.deviceNetworkId}-${it}")
	
		if(foundChildDevice=="" || foundChildDevice==null){
	
			if (logEnable) log.debug "createChildDevice:  Creating Child Device '${device.displayName} (${it})'"
			try {
				def deviceHandlerName = "Virtual Keypad Button Child"
				addChildDevice(deviceHandlerName,
								"${device.deviceNetworkId}-${it}",
								[
									completedSetup: true, 
									label: "${device.displayName} (${it})", 
									isComponent: true, 
									name: "${device.displayName} (${it})",
									componentName: "${it}", 
									componentLabel: "${it}"
								]
							)
				sendEvent(name:"Details", value:"Child device created!  May take some time to display.")
				unschedule(clearDetails)
				runIn(300,clearDetails)
			}
			catch (e) {
				log.error "Child device creation failed with error = ${e}"
				sendEvent(name:"Details", value:"Child device creation failed. Please make sure that the '${deviceHandlerName}' is installed and published.", displayed: true)
			}
		} else {
			if (logEnable) log.debug "createChildDevice: Child Device '${device.displayName} (${it})' found! Skipping"
		}
	}
	
	// create input display
	['InputDisplay'].each {
		foundChildDevice = null
		foundChildDevice = getChildDevice("${device.deviceNetworkId}-${it}")
	
		if(foundChildDevice=="" || foundChildDevice==null){
	
			if (logEnable) log.debug "createChildDevice:  Creating Child Device '${device.displayName} (${it})'"
			try {
				def deviceHandlerName = "Virtual Keypad Input Display Child"
				addChildDevice(deviceHandlerName,
								"${device.deviceNetworkId}-${it}",
								[
									completedSetup: true, 
									label: "${device.displayName} (${it})", 
									isComponent: true, 
									name: "${device.displayName} (${it})",
									componentName: "${it}", 
									componentLabel: "${it}"
								]
							)
				sendEvent(name:"Details", value:"Child device created!  May take some time to display.")
				unschedule(clearDetails)
				runIn(300,clearDetails)
			}
			catch (e) {
				log.error "Child device creation failed with error = ${e}"
				sendEvent(name:"Details", value:"Child device creation failed. Please make sure that the '${deviceHandlerName}' is installed and published.", displayed: true)
			}
		} else {
			if (logEnable) log.debug "createChildDevice: Child Device '${device.displayName} (${it})' found! Skipping"
		}
	}
	
	// create iFrame
	['iFrame'].each {
		foundChildDevice = null
		foundChildDevice = getChildDevice("${device.deviceNetworkId}-${it}")
	
		if(foundChildDevice=="" || foundChildDevice==null){
	
			if (logEnable) log.debug "createChildDevice:  Creating Child Device '${device.displayName} (${it})'"
			try {
				def deviceHandlerName = "Virtual Keypad iFrame Child"
				addChildDevice(deviceHandlerName,
								"${device.deviceNetworkId}-${it}",
								[
									completedSetup: true, 
									label: "${device.displayName} (${it})", 
									isComponent: true, 
									name: "${device.displayName} (${it})",
									componentName: "${it}", 
									componentLabel: "${it}"
								]
							)
				sendEvent(name:"Details", value:"Child device created!  May take some time to display.")
				unschedule(clearDetails)
				runIn(300,clearDetails)
			}
			catch (e) {
				log.error "Child device creation failed with error = ${e}"
				sendEvent(name:"Details", value:"Child device creation failed. Please make sure that the '${deviceHandlerName}' is installed and published.", displayed: true)
			}
		} else {
			if (logEnable) log.debug "createChildDevice: Child Device '${device.displayName} (${it})' found! Skipping"
		}
	}
}

def removeChildren(){
	if (logEnable) log.debug "removing any child devices"
	childDevices.each {
		try{
			if (logEnable) log.debug "removing ${it.deviceNetworkId}"
			deleteChildDevice(it.deviceNetworkId)
		}
		catch (e) {
			if (logEnable) log.debug "Error deleting ${it.deviceNetworkId}: ${e}"
		}
	}
	sendEvent(name:"Details", value:"Child devices removed!  Run the createChildren function when ready to re-build child devices.")
	unschedule(clearDetails)
	runIn(300,clearDetails)
}
 
def removeChild(childNID){
	try{
		if (logEnable) log.debug "removing ${childNID}"
		deleteChildDevice(childNID)
		sendEvent(name:"Details", value:"Child device (${childNID}) removed!")
		unschedule(clearDetails)
		runIn(300,clearDetails)
	}
	catch (e) {
		if (logEnable) log.debug "Error deleting ${childNID}: ${e}"
	}
}


void setCodeLength(length){
    /*
	on install/configure/change
		name		value
		codeLength	length
	*/
    String descriptionText = "${device.displayName} codeLength set to ${length}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name:"codeLength",value:length,descriptionText:descriptionText)
}

void setCode(codeNumber, code, name = null) {
    /*
	on sucess
		name		value								data												notes
		codeChanged	added | changed						[<codeNumber>":["code":"<pinCode>", "name":"<display name for code>"]]	default name to code #<codeNumber>
		lockCodes	JSON map of all lockCode
	*/
 	if (codeNumber == null || codeNumber == 0 || code == null) return

    if (logEnable) log.debug "setCode- ${codeNumber}"	
	
    if (!name) name = "code #${codeNumber}"

    Map lockCodes = getLockCodes()
    Map codeMap = getCodeMap(lockCodes,codeNumber)
    if (!changeIsValid(lockCodes,codeMap,codeNumber,code,name)) return
	
   	Map data = [:]
    String value
	
    if (logEnable) log.debug "setting code ${codeNumber} to ${code} for lock code name ${name}"

    if (codeMap) {
        if (codeMap.name != name || codeMap.code != code) {
            codeMap = ["name":"${name}", "code":"${code}"]
            lockCodes."${codeNumber}" = codeMap
            data = ["${codeNumber}":codeMap]
            if (optEncrypt) data = encrypt(JsonOutput.toJson(data))
            value = "changed"
        }
    } else {
        codeMap = ["name":"${name}", "code":"${code}"]
        data = ["${codeNumber}":codeMap]
        lockCodes << data
        if (optEncrypt) data = encrypt(JsonOutput.toJson(data))
        value = "added"
    }
    updateLockCodes(lockCodes)
    sendEvent(name:"codeChanged",value:value,data:data, isStateChange: true)
}

void deleteCode(codeNumber) {
    /*
	on sucess
		name		value								data
		codeChanged	deleted								[<codeNumber>":["code":"<pinCode>", "name":"<display name for code>"]]
		lockCodes	[<codeNumber>":["code":"<pinCode>", "name":"<display name for code>"],<codeNumber>":["code":"<pinCode>", "name":"<display name for code>"]]
	*/
    Map codeMap = getCodeMap(lockCodes,"${codeNumber}")
    if (codeMap) {
		Map result = [:]
        //build new lockCode map, exclude deleted code
        lockCodes.each{
            if (it.key != "${codeNumber}"){
                result << it
            }
        }
        updateLockCodes(result)
        Map data =  ["${codeNumber}":codeMap]
        //encrypt lockCode data is requested
        if (optEncrypt) data = encrypt(JsonOutput.toJson(data))
        sendEvent(name:"codeChanged",value:"deleted",data:data, isStateChange: true)
    }
}


//helpers
Boolean changeIsValid(lockCodes,codeMap,codeNumber,code,name){
    //validate proposed lockCode change
    Boolean result = true
    Integer maxCodeLength = device.currentValue("codeLength")?.toInteger() ?: 4
    Integer maxCodes = device.currentValue("maxCodes")?.toInteger() ?: 20
    Boolean isBadLength = code.size() > maxCodeLength
    Boolean isBadCodeNum = maxCodes < codeNumber
    if (lockCodes) {
        List nameSet = lockCodes.collect{ it.value.name }
        List codeSet = lockCodes.collect{ it.value.code }
        if (codeMap) {
            nameSet = nameSet.findAll{ it != codeMap.name }
            codeSet = codeSet.findAll{ it != codeMap.code }
        }
        Boolean nameInUse = name in nameSet
        Boolean codeInUse = code in codeSet
        if (nameInUse || codeInUse) {
            if (nameInUse) { log.warn "changeIsValid:false, name:${name} is in use:${ lockCodes.find{ it.value.name == "${name}" } }" }
            if (codeInUse) { log.warn "changeIsValid:false, code:${code} is in use:${ lockCodes.find{ it.value.code == "${code}" } }" }
            result = false
        }
    }
    if (isBadLength || isBadCodeNum) {
        if (isBadLength) { log.warn "changeIsValid:false, length of code ${code} does not match codeLength of ${maxCodeLength}" }
        if (isBadCodeNum) { log.warn "changeIsValid:false, codeNumber ${codeNumber} is larger than maxCodes of ${maxCodes}" }
        result = false
    }
    return result
}

Map getCodeMap(lockCodes,codeNumber){
    Map codeMap = [:]
    Map lockCode = lockCodes?."${codeNumber}"
    if (lockCode) {
        codeMap = ["name":"${lockCode.name}", "code":"${lockCode.code}"]
    }
    return codeMap
}

Map getLockCodes() {
    /*
	on a real lock we would fetch these from the response to a userCode report request
	*/
    String lockCodes = device.currentValue("lockCodes")
    Map result = [:]
    if (lockCodes) {
        //decrypt codes if they're encrypted
        if (lockCodes[0] == "{") result = new JsonSlurper().parseText(lockCodes)
        else result = new JsonSlurper().parseText(decrypt(lockCodes))
    }
    return result
}

void getCodes() {
    //no op
}

void updateLockCodes(lockCodes){
    /*
	whenever a code changes we update the lockCodes event
	*/
    if (logEnable) log.debug "updateLockCodes: ${lockCodes}"
    String strCodes = JsonOutput.toJson(lockCodes)
    if (optEncrypt) {
        strCodes = encrypt(strCodes)
    }
    sendEvent(name:"lockCodes", value:strCodes, isStateChange:true)
}

void updateEncryption(){
    /*
	resend lockCodes map when the encryption option is changed
	*/
    String lockCodes = device.currentValue("lockCodes") //encrypted or decrypted
    if (lockCodes){
        if (optEncrypt && lockCodes[0] == "{") {	//resend encrypted
            sendEvent(name:"lockCodes",value: encrypt(lockCodes))
        } else if (!optEncrypt && lockCodes[0] != "{") {	//resend decrypted
            sendEvent(name:"lockCodes",value: decrypt(lockCodes))
        }
    }
}