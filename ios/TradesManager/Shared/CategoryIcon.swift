import Foundation

/// Turns a shared catalogue icon name into an SF Symbol.
///
/// The *choice* of icon per category is data, shared with Android in
/// `shared/assets/catalog/manifest.json`. This mapping — name to a symbol that
/// exists on this platform — is the part that is properly platform code, and
/// it is deliberately the only place iOS decides what anything looks like.
public enum CategoryIcon {

    /// An unknown name draws a generic box rather than nothing, so a catalogue
    /// that adds a category before the apps do still renders.
    public static let fallback = "shippingbox"

    public static func symbol(for name: String?) -> String {
        switch name {
        case "cable", "wire": return "cable.connector"
        case "conduit": return "line.3.horizontal"
        case "breaker": return "powerplug.fill"
        case "board": return "square.grid.3x3.fill"
        case "outlet": return "powerplug"
        case "earth": return "bolt.fill"

        case "pipe": return "pipe.and.drop.fill"
        case "drain": return "drop.fill"
        case "fitting": return "wrench.and.screwdriver.fill"
        case "valve": return "slider.horizontal.3"
        case "fixture": return "bathtub.fill"
        case "water-heater": return "flame.fill"
        case "meter": return "gauge"

        case "ac-unit", "refrigerant-pipe": return "snowflake"
        case "insulation", "filler", "panel": return "square.stack.3d.up.fill"
        case "gas-cylinder": return "fuelpump.fill"
        case "thermostat": return "thermometer"
        case "duct": return "wind"
        case "service-tool", "power-tool": return "wrench.adjustable.fill"

        case "paint": return "paintbrush.fill"
        case "primer", "waterproofing": return "drop.triangle.fill"

        case "timber": return "hammer.fill"
        case "edge-banding", "rebar": return "ruler.fill"
        case "screw", "hinge": return "gearshape.fill"

        case "cement", "aggregate": return "circle.grid.3x3.fill"
        case "concrete": return "square.3.layers.3d"
        case "block", "tile": return "square.grid.2x2.fill"
        case "site-equipment": return "shippingbox.fill"
        case "ladder": return "stairs"

        case "hand-tool": return "hammer.fill"
        case "measure": return "ruler.fill"
        case "ppe": return "shield.lefthalf.filled"
        case "consumable": return "shippingbox.fill"

        default: return fallback
        }
    }
}
