package edu.cmu.cs214.booking.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.cmu.cs214.booking.domain.Booking;
import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryBookingStoreTest {

    private final Room roomA = new Room("A", "Alpha", 10);
    private final Room roomB = new Room("B", "Beta", 4);
    private final User alice = new User("u1", "Alice");
    private final User bob = new User("u2", "Bob");

    /**
     * Pins the Milestone 1 bug: {@code bookingsForRoom} must filter by room, not
     * return every booking in the store. Booking the same slot in two different
     * rooms must leave each room's list holding only its own booking.
     */
    @Test
    void bookingsForRoomReturnsOnlyThatRoomsBookings() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        store.addBooking(new Booking("b1", roomA, alice, new TimeInterval(600, 660)));
        store.addBooking(new Booking("b2", roomB, bob, new TimeInterval(600, 660)));

        List<Booking> forA = store.bookingsForRoom(roomA);
        assertEquals(1, forA.size());
        assertEquals("b1", forA.get(0).id());

        List<Booking> forB = store.bookingsForRoom(roomB);
        assertEquals(1, forB.size());
        assertEquals("b2", forB.get(0).id());
    }
}
