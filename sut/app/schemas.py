from typing import List, Optional
from pydantic import BaseModel, Field


# User Schemas
class UserAuthRequest(BaseModel):
    username: str = Field(..., min_length=1, max_length=50)
    password: str = Field(..., min_length=1, max_length=100)


class UserResponse(BaseModel):
    id: int
    username: str

    class Config:
        from_attributes = True


# Product Schemas
class ProductResponse(BaseModel):
    id: int
    name: str
    description: Optional[str] = ""
    price: float
    quantity: int

    class Config:
        from_attributes = True


# Cart Schemas
class CartItemAdd(BaseModel):
    product_id: int
    quantity: int = Field(default=1, ge=1)


class CartItemResponse(BaseModel):
    id: int
    product_id: int
    name: str
    price: float
    quantity: int
    subtotal: float
    available_quantity: int


class CartResponse(BaseModel):
    items: List[CartItemResponse]
    total_price: float


class CheckoutResponse(BaseModel):
    detail: str
    order_total: float


class MessageResponse(BaseModel):
    detail: str
