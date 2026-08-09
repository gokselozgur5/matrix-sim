package matrix.machine;

public enum ComputeModel {
    BATTERY("humans as batteries — thermodynamically dubious, the studio cut"),
    PROCESSOR("inmates render their own cells — the original draft");

    public final String desc;

    ComputeModel(String desc) {
        this.desc = desc;
    }
}
