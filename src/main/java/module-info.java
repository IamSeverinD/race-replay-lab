module io.github.iamseverind.racereplay {
    requires javafx.controls;
    requires java.net.http;
    requires com.fasterxml.jackson.databind;

    opens io.github.iamseverind.racereplay.app to javafx.graphics;
}
