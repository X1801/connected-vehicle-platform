package org.cvp.assistant.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataConversationRepository extends JpaRepository<ConversationEntity, String> {
}
