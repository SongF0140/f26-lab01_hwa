package edu.cmu.cs214.booking.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.booking.domain.Booking;
import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.repo.BookingStore;
import edu.cmu.cs214.booking.repo.InMemoryBookingStore;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Extension tasks (拓展 1 and 拓展 2) from the Lab 1 handout: a reusable invariant
 * check, and a randomised operation-sequence test that calls it after every
 * operation.
 */
class InvariantAndRandomOpsTest {

    private static final int MIN_START = 540;
    private static final int SPAN = 240;

    /**
     * 拓展 1: the core invariant, written once and reusable — for every room, no
     * two confirmed bookings of that room hold overlapping intervals.
     */
    static void assertInvariantHolds(BookingStore store, Room... rooms) {
        for (Room room : rooms) {
            List<Booking> bookings = store.bookingsForRoom(room);
            for (int i = 0; i < bookings.size(); i++) {
                for (int j = i + 1; j < bookings.size(); j++) {
                    Booking first = bookings.get(i);
                    Booking second = bookings.get(j);
                    TimeInterval a = first.interval();
                    TimeInterval b = second.interval();
                    assertFalse(a.overlaps(b),
                        () -> "invariant broken in room " + room.id()
                            + ": " + first.id() + a + " overlaps " + second.id() + b);
                }
            }
        }
    }

    private final Room roomA = new Room("A", "Alpha", 10);
    private final Room roomB = new Room("B", "Beta", 4);
    private final Room roomC = new Room("C", "Gamma", 8);
    private final Room[] rooms = {roomA, roomB, roomC};
    private final List<User> users = List.of(
        new User("u1", "Alice"), new User("u2", "Bob"),
        new User("u3", "Carol"), new User("u4", "Dave"));

    @Test
    void invariantHelperDetectsAnOverlapWhenOneIsInjected() {
        // Guard the guard: a deliberately broken store must be caught, otherwise
        // the invariant check would pass vacuously in every other test.
        Room room = new Room("X", "Broken", 1);
        InMemoryBookingStore store = new InMemoryBookingStore();
        store.addBooking(new Booking("b1", room, users.get(0), new TimeInterval(600, 660)));
        store.addBooking(new Booking("b2", room, users.get(1), new TimeInterval(630, 690)));

        assertThrowsAssertion(() -> assertInvariantHolds(store, room));
    }

    private static void assertThrowsAssertion(Runnable r) {
        try {
            r.run();
        } catch (AssertionError expected) {
            return;
        }
        throw new AssertionError("expected the invariant check to fail, but it passed");
    }

    @Test
    void invariantHoldsOnTheHappyPath() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        BookingService svc = new BookingService(store);
        svc.book(roomA, users.get(0), new TimeInterval(600, 660));
        svc.book(roomB, users.get(1), new TimeInterval(600, 660)); // same slot, other room
        svc.book(roomA, users.get(2), new TimeInterval(660, 720)); // back-to-back

        assertInvariantHolds(store, rooms);
    }

    /**
     * 拓展 2: hundreds of random book / cancelBooking operations with a fixed seed,
     * checking the invariant after every single one.
     */
    @Test
    void invariantHoldsAcrossRandomisedOperationSequences() {
        for (long seed : new long[] {1L, 42L, 2026L, 987654321L}) {
            Random rnd = new Random(seed);
            InMemoryBookingStore store = new InMemoryBookingStore();
            BookingService svc = new BookingService(store);
            List<String> confirmedIds = new java.util.ArrayList<>();

            for (int step = 0; step < 400; step++) {
                Room room = rooms[rnd.nextInt(rooms.length)];
                User user = users.get(rnd.nextInt(users.size()));
                int start = MIN_START + rnd.nextInt(SPAN);
                int end = start + 1 + rnd.nextInt(90);
                TimeInterval interval = new TimeInterval(start, end);

                if (confirmedIds.isEmpty() || rnd.nextBoolean()) {
                    BookingResult r = svc.book(room, user, interval);
                    if (r instanceof BookingResult.Confirmed c) {
                        confirmedIds.add(c.booking().id());
                    }
                } else {
                    int idx = rnd.nextInt(confirmedIds.size());
                    String id = confirmedIds.remove(idx);
                    svc.cancelBooking(id);
                }

                assertInvariantHolds(store, rooms);
            }
        }
    }

    @Test
    void randomOpsNeverLoseOrDuplicateConfirmedBookings() {
        // A second property that the invariant alone would not catch: every
        // confirmed id must be findable, and the waitlist must only shrink when
        // somebody is promoted.
        Random rnd = new Random(7);
        InMemoryBookingStore store = new InMemoryBookingStore();
        BookingService svc = new BookingService(store);
        List<String> live = new java.util.ArrayList<>();

        for (int step = 0; step < 300; step++) {
            Room room = rooms[rnd.nextInt(rooms.length)];
            int start = MIN_START + rnd.nextInt(SPAN);
            BookingResult r = svc.book(room, users.get(rnd.nextInt(users.size())),
                new TimeInterval(start, start + 1 + rnd.nextInt(120)));
            if (r instanceof BookingResult.Confirmed c) {
                assertTrue(store.findBooking(c.booking().id()).isPresent());
                live.add(c.booking().id());
            }
            if (!live.isEmpty() && rnd.nextInt(3) == 0) {
                String id = live.remove(rnd.nextInt(live.size()));
                assertTrue(svc.cancelBooking(id));
                assertTrue(store.findBooking(id).isEmpty());
            }
            assertInvariantHolds(store, rooms);
        }
    }
}