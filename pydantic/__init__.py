class BaseModel:
    def __init__(self, **kwargs):
        for k,v in kwargs.items():
            setattr(self,k,v)
    def model_dump(self):
        return self.__dict__.copy()


def Field(default=None, default_factory=None):
    if default_factory is not None:
        return default_factory()
    return default
