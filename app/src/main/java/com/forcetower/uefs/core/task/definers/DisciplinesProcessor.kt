/*
 * This file is part of the UNES Open Source Project.
 * UNES is licensed under the GNU GPLv3.
 *
 * Copyright (c) 2020. João Paulo Sena <joaopaulo761@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.forcetower.uefs.core.task.definers

import android.content.Context
import androidx.room.withTransaction
import com.forcetower.core.extensions.removeSeconds
import com.forcetower.uefs.core.model.unes.Class
import com.forcetower.uefs.core.model.unes.ClassGroup
import com.forcetower.uefs.core.model.unes.ClassLocation
import com.forcetower.uefs.core.model.unes.Discipline
import com.forcetower.uefs.core.storage.database.UDatabase
import com.forcetower.uefs.core.task.UTask
import com.forcetower.uefs.feature.shared.extensions.createTimeInt
import com.forcetower.uefs.feature.shared.extensions.toTitleCase
import com.forcetower.uefs.feature.shared.extensions.toWeekDay
import com.forcetower.uefs.service.NotificationCreator
import dev.forcetower.breaker.model.DisciplineData
import timber.log.Timber

class DisciplinesProcessor(
    private val context: Context,
    private val database: UDatabase,
    private val disciplines: List<DisciplineData>,
    private val semesterId: Long,
    private val localProfileId: Long,
    private val notify: Boolean
) : UTask {
    override suspend fun execute() {
        database.withTransaction {
            val currentSemester = database.semesterDao().getSemestersDirect().maxByOrNull { it.sagresId }
            val allocations = mutableListOf<ClassLocation>()
            disciplines.forEach {
                val resume = if (it.program.isNullOrBlank()) null else it.program
                val discipline = Discipline(name = it.name, code = it.code, credits = it.hours, resume = resume, department = it.department.toTitleCase())
                val disciplineId = database.disciplineDao().insertOrUpdate(discipline)
                Timber.d("Discipline id inserted: $disciplineId at $semesterId")
                val bound = Class(
                    disciplineId = disciplineId,
                    semesterId = semesterId,
                    scheduleOnly = false,
                    missedClasses = it.result?.missedClasses ?: 0,
                    finalScore = it.result?.mean
                )

                val classId = database.classDao().insertNewWays(bound)
                it.classes.forEach { clazz ->
                    val group = ClassGroup(
                        classId = classId,
                        credits = clazz.hours,
                        draft = false,
                        group = clazz.groupName,
                        teacher = clazz.teacher?.name?.toTitleCase(),
                        sagresId = clazz.id
                    )
                    val groupId = database.classGroupDao().insertNewWay(group)
                    if (currentSemester?.uid == semesterId) {
                        clazz.allocations.forEach { allocation ->
                            val time = allocation.time
                            if (time != null) {
                                allocations.add(ClassLocation(
                                    groupId = groupId,
                                    campus = allocation.space?.campus,
                                    modulo = allocation.space?.modulo,
                                    room = allocation.space?.location,
                                    day = (time.day + 1).toWeekDay(),
                                    dayInt = time.day + 1,
                                    startsAt = time.start.removeSeconds(),
                                    endsAt = time.end.removeSeconds(),
                                    startsAtInt = time.start.createTimeInt(),
                                    endsAtInt = time.end.createTimeInt(),
                                    profileId = localProfileId
                                ))
                            }
                        }
                        LectureProcessor(context, database, groupId, clazz.lectures, true).execute()
                    }
                }
                database.gradesDao().putGradesNewWay(classId, it.evaluations, notify)
            }

            database.classLocationDao().putNewSchedule(allocations)

            database.gradesDao().run {
                val posted = getPostedGradesDirect()
                val create = getCreatedGradesDirect()
                val change = getChangedGradesDirect()
                val date = getDateChangedGradesDirect()

                markAllNotified()

                posted.forEach { NotificationCreator.showSagresPostedGradesNotification(it, context) }
                create.forEach { NotificationCreator.showSagresCreateGradesNotification(it, context) }
                change.forEach { NotificationCreator.showSagresChangeGradesNotification(it, context) }
                date.forEach { NotificationCreator.showSagresDateGradesNotification(it, context) }
            }
        }
    }

}