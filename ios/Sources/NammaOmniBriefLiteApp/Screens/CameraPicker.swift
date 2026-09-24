import SwiftUI
import UIKit

public struct CameraPicker: UIViewControllerRepresentable {
    @Environment(\.dismiss) private var dismiss
    public let sourceType: UIImagePickerController.SourceType
    public let onImagePicked: (UIImage) -> Void

    public init(
        sourceType: UIImagePickerController.SourceType = .camera,
        onImagePicked: @escaping (UIImage) -> Void
    ) {
        self.sourceType = UIImagePickerController.isSourceTypeAvailable(sourceType) ? sourceType : .photoLibrary
        self.onImagePicked = onImagePicked
    }

    public func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = sourceType
        picker.delegate = context.coordinator
        return picker
    }

    public func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    public func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    public final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        private let parent: CameraPicker
        init(_ parent: CameraPicker) {
            self.parent = parent
        }

        public func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            if let image = info[.originalImage] as? UIImage {
                parent.onImagePicked(image)
            }
            parent.dismiss()
        }

        public func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }
    }
}
