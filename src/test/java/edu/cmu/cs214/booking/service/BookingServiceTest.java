package edu.cmu.cs214.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.booking.domain.Booking;
import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.repo.InMemoryBookingStore;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookingServiceTest {

    private final Room roomA = new Room("A", "Alpha", 10);
    private final Room roomB = new Room("B", "Beta", 4);
    private final User alice = new User("u1", "Alice");
    private final User bob = new User("u2", "Bob");
    private final User carol = new User("u3", "Carol");
    private final User dave = new User("u4", "Dave");

    private BookingService newService() {
        return new BookingService(new InMemoryBookingStore());
    }

    @Test
    void bookConfirmsWhenRoomIsFree() {
        BookingService svc = newService();
        BookingResult r = svc.book(roomA, alice, new TimeInterval(600, 660));
        assertInstanceOf(BookingResult.Confirmed.class, r);
    }

    @Test
    void bookWaitlistsWhenSlotIsTaken() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        BookingResult r = svc.book(roomA, bob, new TimeInterval(630, 700));
        assertInstanceOf(BookingResult.Waitlisted.class, r);
    }

    @Test
    void backToBackBookingsAreConfirmed() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        BookingResult r = svc.book(roomA, bob, new TimeInterval(660, 720));
        assertInstanceOf(BookingResult.Confirmed.class, r);
    }

    @Test
    void sameSlotInDifferentRoomsAreBothConfirmed() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        BookingResult r = svc.book(roomB, bob, new TimeInterval(600, 660));
        assertInstanceOf(BookingResult.Confirmed.class, r);
    }

    @Test
    void listBookingsReturnsConfirmedBookings() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        svc.book(roomA, bob, new TimeInterval(660, 720));
        assertEquals(2, svc.listBookings(roomA).size());
    }

    // ---- cancel & promotion (S1–S6) ----

    private static String confirmedId(BookingResult r) {
        return ((BookingResult.Confirmed) r).booking().id();
    }

    /** The confirmed booking of {@code user}, or fails if there is none. */
    private static Booking bookingOf(List<Booking> bookings, User user) {
        return bookings.stream()
            .filter(b -> b.user().id().equals(user.id()))
            .findFirst()
            .orElseThrow();
    }

    @Test
    void s1_basicCancelRemovesTheBooking() {
        BookingService svc = newService();
        String id = confirmedId(svc.book(roomA, alice, new TimeInterval(600, 660)));

        assertTrue(svc.cancelBooking(id));

        assertTrue(svc.listBookings(roomA).isEmpty());
    }

    @Test
    void s2_cancelUnknownIdChangesNothing() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        svc.book(roomA, bob, new TimeInterval(630, 700)); // waitlisted

        assertFalse(svc.cancelBooking("no-such-id"));

        assertEquals(1, svc.listBookings(roomA).size());
        assertEquals(1, svc.listWaitlist(roomA).size());
    }

    @Test
    void s3_cancelPromotesTheFirstWaiter() {
        BookingService svc = newService();
        String aliceId = confirmedId(svc.book(roomA, alice, new TimeInterval(600, 660)));
        svc.book(roomA, bob, new TimeInterval(630, 700)); // waitlisted

        assertTrue(svc.cancelBooking(aliceId));

        List<Booking> confirmed = svc.listBookings(roomA);
        assertEquals(1, confirmed.size());
        assertEquals(new TimeInterval(630, 700), bookingOf(confirmed, bob).interval());
        assertTrue(svc.listWaitlist(roomA).isEmpty());
    }

    @Test
    void s4_cancelPromotesAtMostOneWaiterFirstComeFirstServed() {
        BookingService svc = newService();
        String aliceId = confirmedId(svc.book(roomA, alice, new TimeInterval(600, 720)));
        svc.book(roomA, bob, new TimeInterval(600, 660));   // waitlisted first
        svc.book(roomA, carol, new TimeInterval(660, 700)); // waitlisted second

        assertTrue(svc.cancelBooking(aliceId));

        List<Booking> confirmed = svc.listBookings(roomA);
        assertEquals(1, confirmed.size());
        assertEquals(new TimeInterval(600, 660), bookingOf(confirmed, bob).interval());
        assertEquals(1, svc.listWaitlist(roomA).size());
        assertEquals(carol, svc.listWaitlist(roomA).get(0).user());
    }

    @Test
    void s5_cancelSkipsWaitersStillConflicting() {
        BookingService svc = newService();
        String aliceId = confirmedId(svc.book(roomA, alice, new TimeInterval(600, 660)));
        svc.book(roomA, dave, new TimeInterval(700, 760));  // confirmed
        svc.book(roomA, bob, new TimeInterval(630, 730));   // waitlisted first: still conflicts with dave
        svc.book(roomA, carol, new TimeInterval(610, 650)); // waitlisted second: fits once alice is gone

        assertTrue(svc.cancelBooking(aliceId));

        List<Booking> confirmed = svc.listBookings(roomA);
        assertEquals(2, confirmed.size());
        assertEquals(new TimeInterval(610, 650), bookingOf(confirmed, carol).interval());
        assertEquals(new TimeInterval(700, 760), bookingOf(confirmed, dave).interval());
        assertEquals(1, svc.listWaitlist(roomA).size());
        assertEquals(bob, svc.listWaitlist(roomA).get(0).user());
    }

    @Test
    void s6_cancelDoesNotPromoteAcrossRooms() {
        BookingService svc = newService();
        confirmedId(svc.book(roomB, carol, new TimeInterval(600, 660))); // holds room B
        svc.book(roomB, bob, new TimeInterval(600, 660));                // waitlisted for room B
        String aliceId = confirmedId(svc.book(roomA, alice, new TimeInterval(600, 660)));

        assertTrue(svc.cancelBooking(aliceId));

        assertTrue(svc.listBookings(roomA).isEmpty());
        assertTrue(svc.listWaitlist(roomA).isEmpty());
        assertEquals(1, svc.listBookings(roomB).size());
        assertEquals(1, svc.listWaitlist(roomB).size());
        assertEquals(bob, svc.listWaitlist(roomB).get(0).user());
    }

    /**
     * TASK.md canonical scenario: cancelling a booking promotes the waitlisted
     * user into a confirmed booking for their interval.
     */
    @Test
    void cancellingPromotesTheWaitlistedUser() {
        BookingService svc = newService();
        BookingResult first = svc.book(roomA, alice, new TimeInterval(600, 660));
        Booking a = assertInstanceOf(BookingResult.Confirmed.class, first).booking();
        assertInstanceOf(BookingResult.Waitlisted.class,
            svc.book(roomA, bob, new TimeInterval(630, 700)));

        svc.cancelBooking(a.id());

        List<Booking> confirmed = svc.listBookings(roomA);
        assertEquals(1, confirmed.size());
        assertEquals(bob, confirmed.get(0).user());
        assertEquals(new TimeInterval(630, 700), confirmed.get(0).interval());
        assertTrue(svc.listWaitlist(roomA).isEmpty());
    }

    /**
     * Guards the core invariant: if every waiter still conflicts with a remaining
     * booking, cancel promotes nobody.
     */
    @Test
    void cancellingPromotesNoOneWhenNoWaiterFits() {
        BookingService svc = newService();
        String aliceId = confirmedId(svc.book(roomA, alice, new TimeInterval(600, 660)));
        svc.book(roomA, dave, new TimeInterval(700, 760)); // confirmed, stays
        svc.book(roomA, bob, new TimeInterval(630, 730));  // waitlisted, still conflicts with dave

        assertTrue(svc.cancelBooking(aliceId));

        List<Booking> confirmed = svc.listBookings(roomA);
        assertEquals(1, confirmed.size());
        assertEquals(dave, confirmed.get(0).user());
        assertEquals(1, svc.listWaitlist(roomA).size());
        assertEquals(bob, svc.listWaitlist(roomA).get(0).user());
    }

    // ---- isAvailable (Milestone 3: review of the agent's proposed change) ----

    @Test
    void isAvailableReturnsTrueWhenRoomIsFree() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(540, 600)); // touches the query, no overlap

        assertTrue(svc.isAvailable(roomA, new TimeInterval(600, 660)));
    }

    @Test
    void isAvailableReturnsFalseWhenExistingOverlapsFromBefore() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(540, 620)); // starts before, overlaps into [600,660)

        assertFalse(svc.isAvailable(roomA, new TimeInterval(600, 660)));
    }

    @Test
    void isAvailableReturnsFalseWhenExistingContainsInterval() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(540, 780)); // fully contains [600,660)

        assertFalse(svc.isAvailable(roomA, new TimeInterval(600, 660)));
    }

    @Test
    void isAvailableReturnsFalseWhenExistingStartsInsideInterval() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(630, 700)); // starts inside [600,660)

        assertFalse(svc.isAvailable(roomA, new TimeInterval(600, 660)));
    }
}
