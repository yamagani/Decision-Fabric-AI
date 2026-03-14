package com.decisionfabric.domain.shared

sealed class DomainException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class EntityNotFoundException(entityType: String, id: Any) :
    DomainException("$entityType with id '$id' not found")

class ValidationException(message: String) : DomainException(message)

class BusinessRuleViolationException(message: String) : DomainException(message)

class ConflictException(message: String) : DomainException(message)

class AiUnavailableException(message: String, cause: Throwable? = null) : DomainException(message, cause)

class DmnValidationException(message: String, cause: Throwable? = null) : DomainException(message, cause)
