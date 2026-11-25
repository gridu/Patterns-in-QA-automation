from typing import Optional
from fastapi import APIRouter, Depends, HTTPException, status, Response, Request
from sqlalchemy.orm import Session
from app.database import get_db
from app.models import User, CartItem, Product
from app.schemas import UserAuthRequest, UserResponse, MessageResponse
from app.auth_utils import (
    get_session_and_user,
    SESSION_COOKIE_NAME,
    USER_COOKIE_NAME,
)

router = APIRouter(prefix="/api/auth", tags=["Authentication"])


def _merge_guest_cart(guest_session_id: str, user_id: int, db: Session):
    """
    Merges any items added as guest into the authenticated user's cart.
    Ensures that combined quantities do not exceed available product inventory.
    """
    user_cart_key = f"user_{user_id}"
    if guest_session_id == user_cart_key:
        return

    guest_items = db.query(CartItem).filter(CartItem.session_id == guest_session_id).all()
    for g_item in guest_items:
        product = db.query(Product).filter(Product.id == g_item.product_id).first()
        max_available = product.quantity if product else 0

        existing = db.query(CartItem).filter(
            CartItem.session_id == user_cart_key,
            CartItem.product_id == g_item.product_id
        ).first()

        if existing:
            combined = existing.quantity + g_item.quantity
            existing.quantity = min(combined, max_available)
            db.delete(g_item)
        else:
            g_item.session_id = user_cart_key
            g_item.quantity = min(g_item.quantity, max_available)

    db.commit()


@router.post("/register", response_model=UserResponse, status_code=status.HTTP_201_CREATED)
def register(
    payload: UserAuthRequest,
    response: Response,
    request: Request,
    db: Session = Depends(get_db),
    session_data=Depends(get_session_and_user)
):
    guest_session_id, _ = session_data

    existing_user = db.query(User).filter(User.username == payload.username.strip()).first()
    if existing_user:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Username already exists"
        )

    new_user = User(
        username=payload.username.strip(),
        password=payload.password
    )
    db.add(new_user)
    db.commit()
    db.refresh(new_user)

    # Set user session cookie
    response.set_cookie(
        key=USER_COOKIE_NAME,
        value=str(new_user.id),
        httponly=False,
        samesite="lax"
    )

    # Merge guest items to authenticated cart
    _merge_guest_cart(guest_session_id, new_user.id, db)

    return new_user


@router.post("/login", response_model=UserResponse)
def login(
    payload: UserAuthRequest,
    response: Response,
    db: Session = Depends(get_db),
    session_data=Depends(get_session_and_user)
):
    guest_session_id, _ = session_data

    user = db.query(User).filter(
        User.username == payload.username.strip(),
        User.password == payload.password
    ).first()

    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid username or password"
        )

    # Set user session cookie
    response.set_cookie(
        key=USER_COOKIE_NAME,
        value=str(user.id),
        httponly=False,
        samesite="lax"
    )

    # Merge guest items to authenticated cart
    _merge_guest_cart(guest_session_id, user.id, db)

    return user


@router.post("/logout", response_model=MessageResponse)
def logout(response: Response):
    response.delete_cookie(key=USER_COOKIE_NAME)
    return {"detail": "Logged out successfully"}


@router.get("/me", response_model=Optional[UserResponse])
def get_current_user(session_data=Depends(get_session_and_user)):
    _, user = session_data
    return user
