package ru.jeland.os.data.automation

/** Data-only v2 -> v3: retain explicit OFF, omit untouched seats, preserve device order. */
object SeatDeviceMigration {
    val statements:List<String> = buildList {
        val columns=listOf("driverHeating","driverVentilation","passengerHeating","passengerVentilation","rearLeftHeating","rearRightHeating")
        val devices=listOf("DRIVER_SEAT" to listOf(0,1),"PASSENGER_SEAT" to listOf(2,3),
            "REAR_LEFT_SEAT" to listOf(4),"REAR_RIGHT_SEAT" to listOf(5))
        add("UPDATE automation_devices SET position=position*4")
        devices.forEachIndexed {offset,(device,indices)->
            val values=columns.mapIndexed {i,column->if(i in indices)"seats_$column" else "'KEEP'"}.joinToString(",")
            val changed=indices.joinToString(" OR ") {"seats_${columns[it]} != 'KEEP'"}
            val target="ruleId,componentType,position,"+columns.joinToString(",") {"seats_$it"}+",sunshade,steering"
            add("INSERT INTO automation_devices ($target) SELECT ruleId,'$device',position+$offset,$values,NULL,NULL FROM automation_devices WHERE componentType='SEATS' AND ($changed)")
        }
        add("DELETE FROM automation_devices WHERE componentType='SEATS'")
    }
}
