package matrix.entities;

public final class DeletionRefusedException extends RuntimeException {
    public DeletionRefusedException(String message) {
        super(message);
    }
}
