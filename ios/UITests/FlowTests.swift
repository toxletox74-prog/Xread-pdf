import XCTest

/// Parcours réel au doigt : stylo, texte, signature (pose + déplacement), enregistrement d'une copie.
final class FlowTests: XCTestCase {

    private func shot(_ app: XCUIApplication, _ name: String) {
        let png = XCUIScreen.main.screenshot().pngRepresentation
        if let dir = ProcessInfo.processInfo.environment["SHOTS_DIR"] {
            try? png.write(to: URL(fileURLWithPath: dir).appendingPathComponent("ui-\(name).png"))
        }
        let a = XCTAttachment(data: png, uniformTypeIdentifier: "public.png")
        a.name = name
        a.lifetime = .keepAlways
        add(a)
    }

    func testEditSignAndSave() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-uitest-seed", "-uitest-screen", "editor"]
        app.launch()
        let win = app.windows.firstMatch
        XCTAssertTrue(app.buttons["Stylo"].waitForExistence(timeout: 10))
        func at(_ x: CGFloat, _ y: CGFloat) -> XCUICoordinate { win.coordinate(withNormalizedOffset: CGVector(dx: x, dy: y)) }

        // Trait au stylo
        at(0.2, 0.52).press(forDuration: 0.05, thenDragTo: at(0.8, 0.56))
        XCTAssertTrue(app.buttons["Annuler"].isEnabled, "le trait doit être annulable")
        shot(app, "1-trait")

        // Texte
        app.buttons["Texte"].tap()
        at(0.25, 0.45).tap()
        let tv = app.textViews.firstMatch
        let tf = app.textFields.firstMatch
        XCTAssertTrue(tv.waitForExistence(timeout: 3) || tf.waitForExistence(timeout: 1))
        (tv.exists ? tv : tf).typeText("Bonjour Ced")
        app.buttons["OK"].tap()
        XCTAssertTrue(app.buttons["Supprimer"].waitForExistence(timeout: 3), "le texte doit être sélectionné")
        shot(app, "2-texte")

        // Signature : pose puis déplacement
        app.buttons["Signature"].tap()
        at(0.5, 0.62).tap()
        XCTAssertTrue(app.buttons["Supprimer"].waitForExistence(timeout: 3), "la signature doit être sélectionnée")
        at(0.5, 0.62).press(forDuration: 0.05, thenDragTo: at(0.6, 0.68))
        shot(app, "3-signature")

        // Annuler / rétablir
        app.buttons["Annuler"].tap()
        app.buttons["Rétablir"].tap()

        // Enregistrer une copie
        app.buttons["Enregistrer"].tap()
        XCTAssertTrue(app.buttons["Créer une copie"].waitForExistence(timeout: 3))
        app.buttons["Créer une copie"].tap()
        XCTAssertTrue(app.staticTexts["Exemple - modifié"].waitForExistence(timeout: 15), "la copie doit s'ouvrir")
        shot(app, "4-enregistre")
    }
}
