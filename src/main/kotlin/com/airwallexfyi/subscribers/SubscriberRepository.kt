package com.airwallexfyi.subscribers

import java.util.UUID
import org.springframework.data.repository.CrudRepository

interface SubscriberRepository : CrudRepository<SubscriberRecord, UUID>
