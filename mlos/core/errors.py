class MLOSError(Exception):
    pass

class ValidationError(MLOSError):
    pass

class ApprovalRequired(MLOSError):
    pass
