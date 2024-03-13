package org.amplio.csm;

public class CsmEnums{

    public enum tknEvent {
        eNull, House, Circle, Plus, Minus, Tree, Lhand, Rhand, Bowl, Star, Table, House__, Circle__, Plus__, Minus__, Tree__, Lhand__, Rhand__, Bowl__, Star__, Table__, starHouse, starCircle, starPlus, starMinus, starTree, starLhand, starRhand, starBowl, starStar, starTable, AudioStart, AudioDone, ShortIdle, LongIdle, LowBattery, BattCharging, BattCharged, BattMin, BattMax, FirmwareUpdate, Timer, ChargeFault, LithiumHot, MpuHot, FilesSuccess, FilesFail, OK, CANCEL, anyKey, eUNDEF,
    }
    public enum tknAction {
        aNull, LED, bgLED, playSys, playSubject, playMessage, pausePlay, resumePlay, stopPlay, volAdj, spdAdj, posAdj, startRecording, pauseRecording, resumeRecording, finishRecording, playRecording, saveRecording, writeMsg, resumePrevState, saveState, resumeSavedState, subjAdj, msgAdj, setTimer, resetTimer, showCharge, startUSB, endUSB, powerDown, sysBoot, sysTest, playNxtPkg, changePkg, playTune, filesTest, callScript, exitScript, enterState, beginSurvey, endSurvey, writeRecId,
    }

}
