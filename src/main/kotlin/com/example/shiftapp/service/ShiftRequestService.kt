package com.example.shiftapp.service

import com.example.shiftapp.domain.Shift
import com.example.shiftapp.domain.ShiftRequest
import com.example.shiftapp.domain.RequestStatus
import com.example.shiftapp.repository.ShiftRepository
import com.example.shiftapp.repository.ShiftRequestRepository
import org.springframework.stereotype.Service

@Service
class ShiftRequestService(
    private val shiftRepository: ShiftRepository,
    private val shiftRequestRepository: ShiftRequestRepository,
) {
   fun createRequest(requesterId: Long, shiftId: Long, targetUserId: Long): ShiftRequest {
        val shift = shiftRepository.findById(shiftId) ?: throw IllegalArgumentException("Shift not found")
        val request = ShiftRequest(
            id = 0L, // will be set by repository
            shift = shift,
            requesterId = requesterId,
            targetUserId = targetUserId,
            status = RequestStatus.PENDING
        )
        return shiftRequestRepository.save(request)
    }

    fun approveByTargetUser(requestId: Long): ShiftRequest {
        val request = shiftRequestRepository.findById(requestId) 
            ?: throw IllegalArgumentException("Request not found")
        val approvedRequest = request.approveByTargetUser()
        return shiftRequestRepository.save(approvedRequest)
    }
}