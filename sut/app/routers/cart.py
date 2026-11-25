from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from app.database import get_db
from app.models import CartItem, Product
from app.schemas import (
    CartItemAdd,
    CartItemResponse,
    CartResponse,
    CheckoutResponse,
    MessageResponse
)
from app.auth_utils import get_session_and_user, get_active_cart_key, require_auth

router = APIRouter(prefix="/api/cart", tags=["Cart"])


def _calculate_cart(cart_items) -> CartResponse:
    items = []
    total = 0.0
    for item in cart_items:
        price = float(item.product.price)
        subtotal = round(price * item.quantity, 2)
        total += subtotal
        items.append(
            CartItemResponse(
                id=item.id,
                product_id=item.product_id,
                name=item.product.name,
                price=price,
                quantity=item.quantity,
                subtotal=subtotal,
                available_quantity=item.product.quantity
            )
        )
    return CartResponse(items=items, total_price=round(total, 2))


@router.get("", response_model=CartResponse)
def get_cart(
    db: Session = Depends(get_db),
    session_data=Depends(get_session_and_user)
):
    """
    Returns the items in the current active cart and the calculated total price.
    """
    session_id, user = session_data
    cart_key = get_active_cart_key(session_id, user)
    cart_items = db.query(CartItem).filter(CartItem.session_id == cart_key).all()
    return _calculate_cart(cart_items)


@router.post("/items", response_model=CartResponse)
def add_to_cart(
    payload: CartItemAdd,
    db: Session = Depends(get_db),
    session_data=Depends(get_session_and_user)
):
    """
    Adds a product to the cart.
    Validates that requested quantity + existing cart quantity does not exceed available stock.
    """
    session_id, user = session_data
    cart_key = get_active_cart_key(session_id, user)

    product = db.query(Product).filter(Product.id == payload.product_id).first()
    if not product:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Product not found"
        )

    if product.quantity <= 0:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Product is out of stock"
        )

    existing_item = db.query(CartItem).filter(
        CartItem.session_id == cart_key,
        CartItem.product_id == payload.product_id
    ).first()

    current_qty = existing_item.quantity if existing_item else 0
    if current_qty + payload.quantity > product.quantity:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Cannot add {payload.quantity} units. Available stock: {product.quantity} (already in cart: {current_qty})"
        )

    if existing_item:
        existing_item.quantity += payload.quantity
    else:
        new_item = CartItem(
            session_id=cart_key,
            product_id=payload.product_id,
            quantity=payload.quantity
        )
        db.add(new_item)

    db.commit()

    # Return refreshed cart
    cart_items = db.query(CartItem).filter(CartItem.session_id == cart_key).all()
    return _calculate_cart(cart_items)


@router.delete("/items/{product_id}", response_model=MessageResponse)
def remove_from_cart(
    product_id: int,
    db: Session = Depends(get_db),
    session_data=Depends(get_session_and_user)
):
    """
    Deletes an item from the cart.
    """
    session_id, user = session_data
    cart_key = get_active_cart_key(session_id, user)

    item = db.query(CartItem).filter(
        CartItem.session_id == cart_key,
        CartItem.product_id == product_id
    ).first()

    if not item:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Item not found in cart"
        )

    db.delete(item)
    db.commit()
    return {"detail": "Item removed from cart"}


@router.post("/checkout", response_model=CheckoutResponse)
def checkout(
    db: Session = Depends(get_db),
    user=Depends(require_auth)
):
    """
    Completes purchase for the authenticated user.
    Decrements product inventory and empties the cart.
    """
    cart_key = f"user_{user.id}"
    cart_items = db.query(CartItem).filter(CartItem.session_id == cart_key).all()

    if not cart_items:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Cart is empty"
        )

    # Validate stock availability before processing
    total = 0.0
    for item in cart_items:
        if item.product.quantity < item.quantity:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Insufficient inventory for {item.product.name}. Available: {item.product.quantity}, In cart: {item.quantity}"
            )
        total += float(item.product.price) * item.quantity

    # Decrement inventory and remove cart items
    for item in cart_items:
        item.product.quantity -= item.quantity
        db.delete(item)

    db.commit()

    return CheckoutResponse(
        detail="Order placed successfully",
        order_total=round(total, 2)
    )
