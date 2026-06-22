package com.airwallexfyi.digests

import java.util.UUID
import org.springframework.data.repository.CrudRepository

interface DigestDeliveryPostRepository : CrudRepository<DigestDeliveryPostRecord, UUID> {
    fun findByDigestDeliveryIdOrderByDisplayOrderAsc(digestDeliveryId: UUID): List<DigestDeliveryPostRecord>
}
