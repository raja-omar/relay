package dev.relay.place;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PurchaseQuantityPolicyTest {
	@Test
	void oneAtATimeBuysOnlyWhenEmpty() {
		assertEquals(1, PurchaseQuantityPolicy.quantityToBuy(0, 64, true));
		assertEquals(0, PurchaseQuantityPolicy.quantityToBuy(1, 64, true));
		assertEquals(0, PurchaseQuantityPolicy.quantityToBuy(16, 64, true));
	}

	@Test
	void fullStackNeedsNothing() {
		assertEquals(0, PurchaseQuantityPolicy.quantityToBuy(64, 64, false));
		assertEquals(0, PurchaseQuantityPolicy.quantityToBuy(16, 16, false));
	}

	@Test
	void emptyInventoryBuysAFullStack() {
		assertEquals(64, PurchaseQuantityPolicy.quantityToBuy(0, 64, false));
		assertEquals(16, PurchaseQuantityPolicy.quantityToBuy(0, 16, false));
	}

	@Test
	void topsUpAtOrBelowTwentyEight() {
		assertEquals(36, PurchaseQuantityPolicy.quantityToBuy(28, 64, false));
		assertEquals(0, PurchaseQuantityPolicy.quantityToBuy(29, 64, false));
	}

	@Test
	void smallStacksTopUpBeforeTheLastItem() {
		assertEquals(1, PurchaseQuantityPolicy.quantityToBuy(15, 16, false));
		assertEquals(0, PurchaseQuantityPolicy.quantityToBuy(16, 16, false));
	}

	@Test
	void treatsNegativeCountsAsEmpty() {
		assertEquals(64, PurchaseQuantityPolicy.quantityToBuy(-3, 64, false));
		assertEquals(1, PurchaseQuantityPolicy.quantityToBuy(-1, 64, true));
	}
}
